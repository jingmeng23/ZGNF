// Temporary global no-login switch. Disable together with backend LOGIN_DISABLED.
export const loginDisabled = !import.meta.env.VITEST && import.meta.env.VITE_LOGIN_DISABLED !== 'false'
