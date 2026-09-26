/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        ivory: {
          DEFAULT: '#FBF3E7',
          card: '#FFFDF8',
          100: '#FBF3E7',
          200: '#F6E1C9',
        },
        peach: {
          DEFAULT: '#F6E1C9',
          soft: '#FAEBD9',
        },
        sand: {
          DEFAULT: '#EFE1C6',
          line: '#E4D2AE',
        },
        terracotta: {
          DEFAULT: '#B85C38',
          soft: '#E8B79C',
          deep: '#8F4327',
        },
        plum: {
          DEFAULT: '#33233F',
          light: '#4B3459',
          deep: '#221729',
          50: '#F1ECF4',
        },
        coral: {
          DEFAULT: '#FF6B57',
          soft: '#FFD9CF',
          deep: '#E24E3A',
        },
        ink: '#2A2320',
      },
      fontFamily: {
        serif: ['"Fraunces"', 'Georgia', 'serif'],
        sans: ['"Inter"', 'system-ui', '-apple-system', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'SFMono-Regular', 'monospace'],
      },
      boxShadow: {
        soft: '0 2px 10px -2px rgba(51, 35, 63, 0.08), 0 1px 2px rgba(51, 35, 63, 0.06)',
        lifted: '0 12px 32px -8px rgba(51, 35, 63, 0.18)',
        glow: '0 0 0 4px rgba(255, 107, 87, 0.15)',
      },
      borderRadius: {
        xl2: '1.25rem',
      },
    },
  },
  plugins: [],
}
