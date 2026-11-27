package com.zaylope.registration.common.validation;

import java.util.ArrayList;
import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.InitialDirContext;
import org.springframework.stereotype.Component;

/** DNS-only getAttributes: never looks up directory objects or probes SMTP mailboxes. */
@Component
public class JndiEmailDnsResolver implements EmailDnsResolver {
    @Override
    public Answer resolve(String domain) {
        var environment = new Hashtable<String, String>();
        environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
        environment.put("com.sun.jndi.dns.timeout.initial", "500");
        environment.put("com.sun.jndi.dns.timeout.retries", "1");
        InitialDirContext context = null;
        try {
            context = new InitialDirContext(environment);
            var attributes = context.getAttributes(domain + ".", new String[] {"MX", "A", "AAAA"});
            var records = new ArrayList<String>();
            var mx = attributes.get("MX");
            if (mx != null) for (int i = 0; i < mx.size(); i++) records.add(mx.get(i).toString());
            boolean address = attributes.get("A") != null || attributes.get("AAAA") != null;
            return new Answer(Status.ANSWER, java.util.List.copyOf(records), address);
        } catch (NameNotFoundException definitive) {
            return new Answer(Status.NONEXISTENT, java.util.List.of(), false);
        } catch (NamingException temporary) {
            return new Answer(Status.TEMPORARY_FAILURE, java.util.List.of(), false);
        } finally {
            if (context != null) try { context.close(); } catch (NamingException ignored) { }
        }
    }
}
