// Login is required by default; opt into no-login testing explicitly.
export const loginDisabled = !import.meta.env.VITEST && import.meta.env.VITE_LOGIN_DISABLED === 'true'
