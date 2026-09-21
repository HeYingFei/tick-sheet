import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '@/layout/MainLayout.vue'

/** 路由表，与设计方案 §7.4 一致 */
export const menuRoutes = [
  { path: '/dashboard', name: 'dashboard', component: () => import('@/views/Dashboard.vue'), meta: { title: '首页', icon: 'HomeFilled' } },
  { path: '/tasks', name: 'tasks', component: () => import('@/views/TaskList.vue'), meta: { title: '任务列表', icon: 'List' } },
  { path: '/quadrant', name: 'quadrant', component: () => import('@/views/Quadrant.vue'), meta: { title: '四象限看板', icon: 'Grid' } },
  { path: '/calendar', name: 'calendar', component: () => import('@/views/CalendarView.vue'), meta: { title: '日历视图', icon: 'Calendar' } },
  { path: '/timeline', name: 'timeline', component: () => import('@/views/Timeline.vue'), meta: { title: '时间线', icon: 'Clock' } },
  { path: '/stats', name: 'stats', component: () => import('@/views/Stats.vue'), meta: { title: '数据统计', icon: 'TrendCharts' } },
  { path: '/settings', name: 'settings', component: () => import('@/views/Settings.vue'), meta: { title: '系统设置', icon: 'Setting' } }
]

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

// 路由守卫：未登录跳转登录页
router.beforeEach((to) => {
  if (to.meta.public) return true
  const token = localStorage.getItem('token')
  if (!token) {
    return { name: 'login' }
  }
  return true
})

export default router
