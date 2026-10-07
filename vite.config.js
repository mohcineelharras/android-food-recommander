import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

const contentSecurityPolicy =
  "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'"

const securityHeaders = {
  'Content-Security-Policy': `${contentSecurityPolicy}; frame-ancestors 'none'`,
  'X-Content-Type-Options': 'nosniff',
  'Referrer-Policy': 'no-referrer',
  'X-Frame-Options': 'DENY',
}

function productionCsp() {
  return {
    name: 'production-csp',
    transformIndexHtml(html, ctx) {
      if (ctx.server) return html
      const meta = `<meta http-equiv="Content-Security-Policy" content="${contentSecurityPolicy}" />`
      return html.replace('<meta charset="UTF-8" />', `<meta charset="UTF-8" />\n    ${meta}`)
    },
  }
}

export default defineConfig({
  plugins: [react(), productionCsp()],
  server: {
    host: '127.0.0.1',
    strictPort: true,
    fs: { strict: true },
    headers: securityHeaders,
  },
  preview: {
    host: '127.0.0.1',
    headers: securityHeaders,
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/testSetup.js',
    restoreMocks: true,
  },
})
