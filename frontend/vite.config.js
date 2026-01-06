import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react-swc'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  define: {
    // Fix pentru eroarea "global is not defined" din sockjs-client
    global: 'window',
  },
})
