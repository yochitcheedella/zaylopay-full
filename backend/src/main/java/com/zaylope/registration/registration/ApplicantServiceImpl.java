package com.zaylope.registration.registration;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class ApplicantServiceImpl implements ApplicantService {
	private final ApplicantRegistrationWriter registrationWriter;
	private com.zaylope.registration.common.validation.EmailContactPolicy emailPolicy;
	private com.zaylope.registration.common.validation.EmailDomainValidator emailDomains;

	@org.springframework.beans.factory.annotation.Autowired
	public void configureEmailValidation(com.zaylope.registration.common.validation.EmailContactPolicy policy,
			com.zaylope.registration.common.validation.EmailDomainValidator domains) {
		this.emailPolicy = policy;
		this.emailDomains = domains;
	}

	public ApplicantServiceImpl(
			ApplicantRegistrationWriter registrationWriter) {
		this.registrationWriter = registrationWriter;
	}

	@Override
	public ApplicantRegistrationResult register(ApplicantRegistrationRequest request) {
		if (emailPolicy != null) emailDomains.validate(emailPolicy.normalizeNewContact(request.email()));
		try {
			return registrationWriter.register(request);
		} catch (DataIntegrityViolationException exception) {
			return registrationWriter.register(request);
		}
	}
}
