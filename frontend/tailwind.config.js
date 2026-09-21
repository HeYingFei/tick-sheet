/**
 * 语义色统一走 CSS 变量（见 src/styles/index.css）。
 * 深色模式只需在该文件里覆盖变量值，页面代码不必到处写 dark: 前缀。
 */
const token = (name) => `rgb(var(${name}) / <alpha-value>)`

/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{vue,js}'],
  // 关闭 preflight：其全局 reset 会覆盖 Element Plus 的按钮/表单默认样式
  corePlugins: {
    preflight: false
  },
  theme: {
    extend: {
      colors: {
        // 基底：冷调纸灰，刻意避开纯白与常见的暖奶油底色
        paper: token('--c-paper'),
        surface: token('--c-surface'),
        raised: token('--c-raised'),
        ink: token('--c-ink'),
        graphite: token('--c-graphite'),
        faint: token('--c-faint'),
        rule: token('--c-rule'),
        'rule-strong': token('--c-rule-strong'),

        primary: {
          DEFAULT: token('--c-primary'),
          light: token('--c-primary-light'),
          dark: token('--c-primary-dark')
        },

        // 四象限取印刷颜料色系，不用 Tailwind 默认色板
        quadrant: {
          q1: token('--c-q1'),
          q2: token('--c-q2'),
          q3: token('--c-q3'),
          q4: token('--c-q4')
        },

        status: {
          todo: token('--c-status-todo'),
          doing: token('--c-status-doing'),
          done: token('--c-status-done'),
          canceled: token('--c-status-canceled'),
          overdue: token('--c-status-overdue')
        }
      },
      fontFamily: {
        sans: ['IBM Plex Sans', 'system-ui', '-apple-system', 'PingFang SC', 'Microsoft YaHei', 'sans-serif'],
        mono: ['IBM Plex Mono', 'ui-monospace', 'Cascadia Mono', 'Consolas', 'monospace']
      },
      borderRadius: {
        card: '4px'
      },
      boxShadow: {
        card: '0 1px 2px rgb(var(--c-shadow) / 0.05)',
        'card-hover': '0 2px 10px rgb(var(--c-shadow) / 0.08)'
      }
    }
  },
  plugins: []
}
