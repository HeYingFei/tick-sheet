/**
 * 玻璃光源控制器。
 *
 * 把「指针位置 + 滚动漂移」写入根级 CSS 变量 --glass-light-x / --glass-light-y（0–1），
 * 所有玻璃面板的 sheen 高光消费这两个变量，于是整屏玻璃共享同一光源、随交互流动，
 * 读起来像被同一盏灯照着的光学材质，而不是各贴各的静态反光。
 *
 * 纯 CSS 变量驱动，不依赖 Chromium 折射能力，全内核生效。
 * 无输入时回落到固定左上光，永不比静态差；prefers-reduced-motion 下冻结。
 */

const DEFAULT_X = 0.2
const DEFAULT_Y = 0.05
/** 缓动系数：越小光斑移动越「液态」 */
const EASE = 0.08
/** 滚动带来的纵向漂移幅度（±） */
const SCROLL_DRIFT = 0.16

export function startGlassLight() {
  const root = document.documentElement
  const reduce =
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches

  const write = (x, y) => {
    root.style.setProperty('--glass-light-x', x.toFixed(4))
    root.style.setProperty('--glass-light-y', y.toFixed(4))
  }

  const cleanup = () => {
    root.style.removeProperty('--glass-light-x')
    root.style.removeProperty('--glass-light-y')
  }

  // 前庭敏感：固定到默认光源，不监听、不动画
  if (reduce) {
    write(DEFAULT_X, DEFAULT_Y)
    return cleanup
  }

  let pointerX = DEFAULT_X
  let pointerY = DEFAULT_Y
  let scrollDrift = 0

  let curX = DEFAULT_X
  let curY = DEFAULT_Y
  let raf = 0

  const targetX = () => pointerX
  const targetY = () => Math.min(1, Math.max(0, pointerY + scrollDrift))

  function tick() {
    const tx = targetX()
    const ty = targetY()
    curX += (tx - curX) * EASE
    curY += (ty - curY) * EASE
    write(curX, curY)
    if (Math.abs(tx - curX) > 0.001 || Math.abs(ty - curY) > 0.001) {
      raf = requestAnimationFrame(tick)
    } else {
      raf = 0
    }
  }

  function schedule() {
    if (!raf) raf = requestAnimationFrame(tick)
  }

  function onPointer(e) {
    pointerX = Math.min(1, Math.max(0, e.clientX / window.innerWidth))
    pointerY = Math.min(1, Math.max(0, e.clientY / window.innerHeight))
    schedule()
  }

  function onScroll() {
    // 每滚动 480px 让光源纵向往返漂移一次，产生「光在玻璃上流动」的感觉
    const phase = (window.scrollY % 480) / 480
    scrollDrift = (phase - 0.5) * SCROLL_DRIFT
    schedule()
  }

  window.addEventListener('pointermove', onPointer, { passive: true })
  window.addEventListener('scroll', onScroll, { passive: true, capture: true })
  write(curX, curY)

  return () => {
    window.removeEventListener('pointermove', onPointer)
    window.removeEventListener('scroll', onScroll, { capture: true })
    if (raf) cancelAnimationFrame(raf)
    cleanup()
  }
}
