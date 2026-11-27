package com.zaylope.registration.common.validation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/validation")
public class EmailPolicyController {
    private final EmailContactPolicy policy;
    public EmailPolicyController(EmailContactPolicy policy) { this.policy = policy; }
    @GetMapping("/email-policy")
    public java.util.Map<String, Object> emailPolicy() { return policy.publicPolicy(); }
}
