package com.zaylope.registration.common.validation;

import java.util.List;

public interface EmailDnsResolver {
    enum Status { ANSWER, NONEXISTENT, TEMPORARY_FAILURE }
    record Answer(Status status, List<String> mx, boolean hasAddress) {}
    Answer resolve(String domain);
}
