package com.zaylope.registration.verification;

import com.zaylope.registration.config.VerificationProperties;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Durable, pseudonymized limits. Independent transactions preserve counters on delivery/validation failures. */
@Service
public class OtpAbuseGuard {
    private final JdbcTemplate jdbc;
    private final ApplicantOtpHasher hasher;
    private final VerificationProperties properties;
    private final Clock clock;
    private final int cumulativeAttempts;
    private final int ipSends;
    private final int ipAttempts;
    public OtpAbuseGuard(JdbcTemplate jdbc, ApplicantOtpHasher hasher, VerificationProperties properties, Clock clock,
            @Value("${app.verification.max-cumulative-attempts:15}") int cumulativeAttempts,
            @Value("${app.verification.max-ip-sends:20}") int ipSends,
            @Value("${app.verification.max-ip-attempts:100}") int ipAttempts) {
        this.jdbc=jdbc; this.hasher=hasher; this.properties=properties; this.clock=clock;
        if(cumulativeAttempts<1||ipSends<1||ipAttempts<1) throw new IllegalArgumentException("OTP limits must be positive");
        this.cumulativeAttempts=cumulativeAttempts; this.ipSends=ipSends; this.ipAttempts=ipAttempts;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW, noRollbackFor=OtpRateLimitException.class)
    public void check(long applicantId, ApplicantVerificationChannel channel, String destination, String ip, boolean send) {
        checkBudget(applicantId, channel, destination, ip, send ? "send" : "attempt");
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void changeContact(long applicantId, ApplicantVerificationChannel channel, String destination, String ip) {
        checkBudget(applicantId, channel, destination, ip, "change");
    }
    private void checkBudget(long applicantId, ApplicantVerificationChannel channel, String destination, String ip, String operation) {
        boolean send = !"attempt".equals(operation);
        Instant now=clock.instant();
        List<String> scopes=List.of("app:"+applicantId+":"+channel,"dest:"+channel+":"+destination,"ip:"+ip);
        var updates = new java.util.ArrayList<Object[]>();
        for(int index=0; index<scopes.size(); index++) {
            // Public login has no applicant yet: a shared app:0 bucket would throttle every account together.
            // Destination and IP budgets still apply uniformly before any account lookup.
            if (index == 0 && applicantId <= 0) continue;
            String key=hasher.fingerprint("OTP_LIMIT:"+operation+":"+scopes.get(index));
            jdbc.update("INSERT IGNORE INTO otp_abuse_limits (bucket_hash,window_started_at,request_count) VALUES (?,?,0)", key, Timestamp.from(now));
            var bucket=jdbc.queryForMap("SELECT window_started_at,last_sent_at,request_count FROM otp_abuse_limits WHERE bucket_hash=? FOR UPDATE",key);
            Instant start=((Timestamp)bucket.get("window_started_at")).toInstant();
            Instant last=bucket.get("last_sent_at")==null?null:((Timestamp)bucket.get("last_sent_at")).toInstant();
            int count=((Number)bucket.get("request_count")).intValue();
            if(!now.isBefore(start.plus(properties.sendWindow()))) { start=now; count=0; }
            int max=index==2?(send?ipSends:ipAttempts):(send?properties.maxSendsPerWindow():cumulativeAttempts);
            if ("change".equals(operation) && index<2) max=3;
            var scope = index == 2 ? OtpRateLimitException.Scope.IP : index == 0
                    ? OtpRateLimitException.Scope.APPLICANT : OtpRateLimitException.Scope.IDENTIFIER;
            if(count>=max) throw new OtpRateLimitException("OTP_RATE_LIMITED",seconds(now,start.plus(properties.sendWindow())),
                    scope, now, start.plus(properties.sendWindow()));
            if(send && index<2 && last!=null && now.isBefore(last.plus(properties.resendCooldown())))
                throw new OtpRateLimitException("OTP_RESEND_TOO_SOON",seconds(now,last.plus(properties.resendCooldown())),
                        scope, now, last.plus(properties.resendCooldown()));
            updates.add(new Object[]{Timestamp.from(start),send?Timestamp.from(now):null,count+1,key});
        }
        for (Object[] update : updates) jdbc.update("UPDATE otp_abuse_limits SET window_started_at=?,last_sent_at=?,request_count=? WHERE bucket_hash=?", update);
    }
    private long seconds(Instant now,Instant until) { return Math.max(1,(Duration.between(now,until).toMillis()+999)/1000); }
}

