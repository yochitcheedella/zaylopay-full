package com.zaylope.registration.affiliate;

/**
 * Separated domain lifecycle statuses for payments, memberships, and affiliate accounts.
 * Kept distinct from manual admin approval statuses.
 */
public final class MembershipLifecycle {

	private MembershipLifecycle() {
	}

	public enum PaymentLifecycleStatus {
		PENDING,
		CAPTURED,
		FAILED,
		REFUNDED
	}

	public enum MembershipStatus {
		NOT_STARTED,
		PENDING_ACTIVATION,
		ACTIVE,
		REVOKED
	}

	public enum AffiliateAccountStatus {
		PENDING,
		ACTIVE,
		SUSPENDED,
		CLOSED
	}
}
