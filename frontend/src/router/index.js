import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '@/layout/MainLayout.vue'

/** 路由表，与设计方案 §7.4 一致。adminOnly 的项仅超级管理员可见 */
export const menuRoutes = [
  { path: '/dashboard', name: 'dashboard', component: () => import('@/views/Dashboard.vue'), meta: { title: '首页', icon: 'HomeFilled' } },
  { path: '/tasks', name: 'tasks', component: () => import('@/views/TaskList.vue'), meta: { title: '任务列表', icon: 'List' } },
  { path: '/quadrant', name: 'quadrant', component: () => import('@/views/Quadrant.vue'), meta: { title: '四象限看板', icon: 'Grid' } },
  { path: '/calendar', name: 'calendar', component: () => import('@/views/CalendarView.vue'), meta: { title: '日历视图', icon: 'Calendar' } },
  { path: '/timeline', name: 'timeline', component: () => import('@/views/Timeline.vue'), meta: { title: '时间线', icon: 'Clock' } },
  { path: '/stats', name: 'stats', component: () => import('@/views/Stats.vue'), meta: { title: '数据统计', icon: 'TrendCharts' } },
  { path: '/settings', name: 'settings', component: () => import('@/views/Settings.vue'), meta: { title: '系统设置', icon: 'Setting' } },
  { path: '/users', name: 'users', component: () => import('@/views/UserManage.vue'), meta: { title: '用户管理', icon: 'UserFilled', adminOnly: true } }
]

/**
 * 从本地存的身份信息里取角色。
 *
 * <p>守卫刻意不依赖 Pinia：可以直接读 localStorage，也避免引入 store 初始化时机的耦合。
 */
function storedRole() {
  try {
    return JSON.parse(localStorage.getItem('user') || 'null')?.role || ''
  } catch {
    return ''
  }
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/Login.vue'), meta: { public: true } },
    {
      path: '/',
      component: MainLayout,
      redirect: '/dashboard',
      children: menuRoutes
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
  ]
})

// 路由守卫：未登录跳登录页；管理员专属页面拦下普通用户。
// 这里只是体验优化——真正的权限边界在后端 AdminOnlyInterceptor。
router.beforeEach((to) => {
  if (to.meta.public) return true
  const token = localStorage.getItem('token')
  if (!token) {
    return { name: 'login' }
  }
  if (to.meta.adminOnly && storedRole() !== 'admin') {
    return { name: 'dashboard' }
  }
  return true
})

export default router
