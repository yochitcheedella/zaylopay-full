/**
 * Centralized post-authentication destination resolver for ZayloPe.
 * Separates authentication outcomes from navigation so /welcome can be swapped
 * for the full affiliate dashboard later without rewriting login logic.
 */
export const WELCOME_ROUTE = '/welcome';

export function resolvePostAuthRoute(eligibleStep) {
  switch (eligibleStep) {
    case 'DASHBOARD':
    case 'WELCOME':
    case 'MEMBERSHIP_OVERVIEW':
      return WELCOME_ROUTE;
    case 'ACCOUNT':
      return '/register?step=1';
    case 'VERIFY':
      return '/register?step=2';
    case 'PAYMENT':
    case 'REVIEW':
      return '/register?step=3';
    case 'PAYMENT_STATUS':
    case 'STATUS':
    case 'ACTIVATION_RECOVERY':
    case 'RESTRICTED':
      return '/register?step=4';
    default:
      return '/register';
  }
}
