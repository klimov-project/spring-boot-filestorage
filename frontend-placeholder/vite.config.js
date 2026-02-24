import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  base: process.env.VITE_BASE || '/',
  plugins: [react()],
  // server: {
  //   host: true,
  //   port: 80,
  //   allowedHosts: 'mydev.local',
  //   proxy: {
  //     '/api': {
  //       target: 'http://localhost:8080',
  //       changeOrigin: true,
  //       secure: false,
  //       headers: {
  //         Origin: 'http://localhost:8080',
  //       },
  //     },
  //   },
  // },

})
