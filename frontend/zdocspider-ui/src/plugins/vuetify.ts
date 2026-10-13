/**
 * plugins/vuetify.ts
 *
 * Framework documentation: https://vuetifyjs.com`
 */

// Composables
import { createVuetify } from 'vuetify'
// Styles
import '@mdi/font/css/materialdesignicons.css'
import 'vuetify/styles'

// https://vuetifyjs.com/en/introduction/why-vuetify/#feature-guides
export default createVuetify({
  theme: {
    defaultTheme: 'dark',
    themes: {
      dark: {
        dark: true,
        colors: {
          background: '#121316',
          surface: '#1b1c20',
          'surface-variant': '#24262c',
          'surface-bright': '#2f3139',
          primary: '#38bdf8',
          'primary-darken-1': '#0284c7',
          secondary: '#94a3b8',
          'secondary-darken-1': '#64748b',
          accent: '#818cf8',
          error: '#f87171',
          info: '#38bdf8',
          success: '#34d399',
          warning: '#fbbf24',
        },
      },
      light: {
        dark: false,
        colors: {
          background: '#f8fafc',
          surface: '#ffffff',
          'surface-variant': '#f1f5f9',
          'surface-bright': '#e2e8f0',
          primary: '#0284c7',
          'primary-darken-1': '#0369a1',
          secondary: '#64748b',
          'secondary-darken-1': '#475569',
          accent: '#6366f1',
          error: '#ef4444',
          info: '#0284c7',
          success: '#10b981',
          warning: '#f59e0b',
        },
      },
    },
  },
})
