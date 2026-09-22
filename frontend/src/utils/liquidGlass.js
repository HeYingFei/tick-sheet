/**
 * Liquid Glass — SVG feDisplacementMap 物理折射。
 *
 * 折射管线参考 archisvaze/liquid-glass 的实现思路：
 *   凸超椭圆表面 → Snell 剖面 → 圆角斜面位移图 + 镜面高光图
 *   → feGaussianBlur + feDisplacementMap + saturate + specular 合成
 *   → backdrop-filter: url(#id)
 *
 * 注意：上游仓库并未声明任何许可证（无 LICENSE 文件、README 亦未提及），
 * 因此此处不作「移植自 …（MIT）」的表述，也不主张来自上游的授权。
 * 若日后需要更清晰的权属，应改为按折射原理独立实现。
 *
 * 只在 Chromium 启用（backdrop-filter: url() 支持）。
 * 其它内核由 index.css 的毛玻璃材质栈回落，不依赖本模块。
 */

const SVG_NS = 'http://www.w3.org/2000/svg'

/** 凸超椭圆（近似 iOS 连续圆角）表面高度 */
function convexSquircle(x) {
  return Math.pow(1 - Math.pow(1 - x, 4), 0.25)
}

/** Snell 折射剖面：斜面内各采样点的水平位移 */
function calculateRefractionProfile(glassThickness, bezelWidth, heightFn, ior, samples = 128) {
  const eta = 1 / ior
  function refract(nx, ny) {
    const dot = ny
    const k = 1 - eta * eta * (1 - dot * dot)
    if (k < 0) return null
    const sq = Math.sqrt(k)
    return [-(eta * dot + sq) * nx, eta - (eta * dot + sq) * ny]
  }
  const profile = new Float64Array(samples)
  for (let i = 0; i < samples; i++) {
    const x = i / samples
    const y = heightFn(x)
    const dx = x < 1 ? 0.0001 : -0.0001
    const y2 = heightFn(x + dx)
    const deriv = (y2 - y) / dx
    const mag = Math.sqrt(deriv * deriv + 1)
    const ref = refract(-deriv / mag, -1 / mag)
    profile[i] = ref ? ref[0] * ((y * bezelWidth + glassThickness) / ref[1]) : 0
  }
  return profile
}

/** 斜面带位移图：R/G = 水平/垂直位移，128 = 无位移 */
function generateDisplacementMap(w, h, radius, bezelWidth, profile, maxDisp) {
  const c = document.createElement('canvas')
  c.width = w
  c.height = h
  const ctx = c.getContext('2d', { willReadFrequently: true })
  const img = ctx.createImageData(w, h)
  const d = img.data
  for (let i = 0; i < d.length; i += 4) {
    d[i] = 128
    d[i + 1] = 128
    d[i + 2] = 0
    d[i + 3] = 255
  }

  const r = radius
  const rSq = r * r
  const r1Sq = (r + 1) ** 2
  const rBSq = Math.max(r - bezelWidth, 0) ** 2
  const wB = w - r * 2
  const hB = h - r * 2
  const S = profile.length

  for (let y1 = 0; y1 < h; y1++) {
    for (let x1 = 0; x1 < w; x1++) {
      const x = x1 < r ? x1 - r : x1 >= w - r ? x1 - r - wB : 0
      const y = y1 < r ? y1 - r : y1 >= h - r ? y1 - r - hB : 0
      const dSq = x * x + y * y
      if (dSq > r1Sq || dSq < rBSq) continue
      const dist = Math.sqrt(dSq)
      const fromSide = r - dist
      const op = dSq < rSq ? 1 : 1 - (dist - Math.sqrt(rSq)) / (Math.sqrt(r1Sq) - Math.sqrt(rSq))
      if (op <= 0 || dist === 0) continue
      const cos = x / dist
      const sin = y / dist
      const bi = Math.min(((fromSide / bezelWidth) * S) | 0, S - 1)
      const disp = profile[bi] || 0
      const dX = (-cos * disp) / maxDisp
      const dY = (-sin * disp) / maxDisp
      const idx = (y1 * w + x1) * 4
      d[idx] = (128 + dX * 127 * op + 0.5) | 0
      d[idx + 1] = (128 + dY * 127 * op + 0.5) | 0
    }
  }
  ctx.putImageData(img, 0, 0)
  return c.toDataURL()
}

/** 斜面镜面高光图：表面曲率 × 光照方向 */
function generateSpecularMap(w, h, radius, bezelWidth, angle = Math.PI / 3) {
  const c = document.createElement('canvas')
  c.width = w
  c.height = h
  const ctx = c.getContext('2d', { willReadFrequently: true })
  const img = ctx.createImageData(w, h)
  const d = img.data
  d.fill(0)

  const r = radius
  const rSq = r * r
  const r1Sq = (r + 1) ** 2
  const rBSq = Math.max(r - bezelWidth, 0) ** 2
  const wB = w - r * 2
  const hB = h - r * 2
  const sv = [Math.cos(angle), Math.sin(angle)]

  for (let y1 = 0; y1 < h; y1++) {
    for (let x1 = 0; x1 < w; x1++) {
      const x = x1 < r ? x1 - r : x1 >= w - r ? x1 - r - wB : 0
      const y = y1 < r ? y1 - r : y1 >= h - r ? y1 - r - hB : 0
      const dSq = x * x + y * y
      if (dSq > r1Sq || dSq < rBSq) continue
      const dist = Math.sqrt(dSq)
      const fromSide = r - dist
      const op = dSq < rSq ? 1 : 1 - (dist - Math.sqrt(rSq)) / (Math.sqrt(r1Sq) - Math.sqrt(rSq))
      if (op <= 0 || dist === 0) continue
      const cos = x / dist
      const sin = -y / dist
      const dot = Math.abs(cos * sv[0] + sin * sv[1])
      const edge = Math.sqrt(Math.max(0, 1 - (1 - fromSide) ** 2))
      const coeff = dot * edge
      const col = (255 * coeff) | 0
      const alpha = (col * coeff * op) | 0
      const idx = (y1 * w + x1) * 4
      d[idx] = col
      d[idx + 1] = col
      d[idx + 2] = col
      d[idx + 3] = alpha
    }
  }
  ctx.putImageData(img, 0, 0)
  return c.toDataURL()
}

let svgRoot = null
let filterSeq = 0
const mapCache = new Map()
const bound = new WeakMap()

function ensureSvgRoot() {
  if (svgRoot?.isConnected) return svgRoot
  svgRoot = document.createElementNS(SVG_NS, 'svg')
  svgRoot.setAttribute('width', '0')
  svgRoot.setAttribute('height', '0')
  svgRoot.setAttribute('aria-hidden', 'true')
  svgRoot.setAttribute('color-interpolation-filters', 'sRGB')
  svgRoot.style.cssText = 'position:absolute;overflow:hidden;pointer-events:none;width:0;height:0'
  svgRoot.appendChild(document.createElementNS(SVG_NS, 'defs'))
  document.body.appendChild(svgRoot)
  return svgRoot
}

/** Chromium 专用：backdrop-filter 支持 url() */
export function supportsLiquidRefraction() {
  if (typeof CSS === 'undefined' || typeof CSS.supports !== 'function') return false
  if (!CSS.supports('backdrop-filter', 'blur(1px)')) return false
  const ua = navigator.userAgent
  return /Chrome\/|Edg\/|Chromium\//.test(ua) && !/Firefox\//.test(ua)
}

/**
 * 生成/复用一块玻璃的 SVG 滤镜。
 * @param {{width:number,height:number,radius?:number,bezel?:number,thickness?:number,ior?:number,blur?:number,scaleRatio?:number,specularOpacity?:number,specularSaturation?:number}} opts
 */
export function buildGlassFilter(opts) {
  const w = Math.max(2, Math.round(opts.width))
  const h = Math.max(2, Math.round(opts.height))
  const radius = Math.max(2, Math.min(opts.radius ?? 20, Math.min(w, h) / 2 - 1))
  const bezel = Math.max(2, Math.min(opts.bezel ?? 12, radius - 1, Math.min(w, h) / 2 - 1))
  const thickness = opts.thickness ?? 80
  const ior = opts.ior ?? 2.4
  const blur = opts.blur ?? 0.4
  const scaleRatio = opts.scaleRatio ?? 1
  const specularOpacity = opts.specularOpacity ?? 0.5
  const specularSaturation = opts.specularSaturation ?? 4

  const key = `${w}x${h}|r${radius}|b${bezel}|t${thickness}|i${ior}|bl${blur}|sr${scaleRatio}`
  let maps = mapCache.get(key)
  if (!maps) {
    const profile = calculateRefractionProfile(thickness, bezel, convexSquircle, ior, 128)
    let maxDisp = 0
    for (let i = 0; i < profile.length; i++) {
      const a = Math.abs(profile[i])
      if (a > maxDisp) maxDisp = a
    }
    if (!maxDisp) maxDisp = 1
    maps = {
      dispUrl: generateDisplacementMap(w, h, radius, bezel, profile, maxDisp),
      specUrl: generateSpecularMap(w, h, radius, bezel * 2.5),
      scale: maxDisp * scaleRatio
    }
    if (mapCache.size > 64) mapCache.clear()
    mapCache.set(key, maps)
  }

  const id = `liquid-glass-${++filterSeq}`
  const defs = ensureSvgRoot().querySelector('defs')
  const filter = document.createElementNS(SVG_NS, 'filter')
  filter.setAttribute('id', id)
  filter.setAttribute('x', '0%')
  filter.setAttribute('y', '0%')
  filter.setAttribute('width', '100%')
  filter.setAttribute('height', '100%')
  filter.innerHTML = [
    `<feGaussianBlur in="SourceGraphic" stdDeviation="${blur}" result="blurred_source" />`,
    `<feImage href="${maps.dispUrl}" x="0" y="0" width="${w}" height="${h}" result="disp_map" />`,
    `<feDisplacementMap in="blurred_source" in2="disp_map" scale="${maps.scale}" xChannelSelector="R" yChannelSelector="G" result="displaced" />`,
    `<feColorMatrix in="displaced" type="saturate" values="${specularSaturation}" result="displaced_sat" />`,
    `<feImage href="${maps.specUrl}" x="0" y="0" width="${w}" height="${h}" result="spec_layer" />`,
    `<feComposite in="displaced_sat" in2="spec_layer" operator="in" result="spec_masked" />`,
    `<feComponentTransfer in="spec_layer" result="spec_faded"><feFuncA type="linear" slope="${specularOpacity}" /></feComponentTransfer>`,
    `<feBlend in="spec_masked" in2="displaced" mode="normal" result="with_sat" />`,
    `<feBlend in="spec_faded" in2="with_sat" mode="normal" />`
  ].join('')
  defs.appendChild(filter)
  return { id, filter: `url(#${id})`, filterEl: filter }
}

/**
 * 挂到节点：写入 --liquid-filter，尺寸变化时重建滤镜。
 * @param {HTMLElement} el
 * @param {object} [opts] 传给 buildGlassFilter 的参数
 * @returns {() => void} 卸载函数
 */
export function applyLiquidGlass(el, opts = {}) {
  if (!el || !supportsLiquidRefraction()) return () => {}

  const prev = bound.get(el)
  if (prev) {
    prev.ro.disconnect()
    prev.filterEl?.remove()
  }

  const rebuild = () => {
    const rect = el.getBoundingClientRect()
    if (rect.width < 4 || rect.height < 4) return
    const cur = bound.get(el)
    if (cur?.filterEl) cur.filterEl.remove()
    const radius =
      opts.radius ?? (parseFloat(getComputedStyle(el).borderTopLeftRadius) || 20)
    const built = buildGlassFilter({
      ...opts,
      width: rect.width,
      height: rect.height,
      radius
    })
    el.style.setProperty('--liquid-filter', built.filter)
    bound.set(el, { filterEl: built.filterEl, ro: cur?.ro ?? null })
  }

  let ro = prev?.ro
  if (!ro) {
    let timer = 0
    ro = new ResizeObserver(() => {
      clearTimeout(timer)
      timer = setTimeout(rebuild, 80)
    })
    ro.observe(el)
  }
  bound.set(el, { filterEl: null, ro })
  rebuild()

  return () => {
    ro.disconnect()
    const cur = bound.get(el)
    if (cur?.filterEl) cur.filterEl.remove()
    el.style.removeProperty('--liquid-filter')
    bound.delete(el)
  }
}

/**
 * 批量挂载 + MutationObserver 跟进动态节点（弹窗、路由切换后的新卡片）。
 * @param {string} selector
 * @param {object} [opts]
 */
export function applyLiquidGlassAll(selector, opts = {}) {
  if (!supportsLiquidRefraction()) return () => {}
  const offs = new Map()

  const scan = () => {
    for (const el of document.querySelectorAll(selector)) {
      if (!offs.has(el)) offs.set(el, applyLiquidGlass(el, opts))
    }
  }

  scan()
  const mo = new MutationObserver(scan)
  mo.observe(document.body, { childList: true, subtree: true })

  return () => {
    mo.disconnect()
    offs.forEach((fn) => fn())
    offs.clear()
  }
}
