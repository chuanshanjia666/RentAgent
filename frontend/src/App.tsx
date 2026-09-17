import React from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth'
import NavLayout from './components/NavLayout'
import LoginView from './views/LoginView'
import HomeView from './views/HomeView'
import HouseDetailView from './views/HouseDetailView'
import AiChatView from './views/AiChatView'
import FavoritesView from './views/FavoritesView'
import AppointmentsView from './views/AppointmentsView'
import ContractsView from './views/ContractsView'
import OrdersView from './views/OrdersView'
import NotificationsView from './views/NotificationsView'
import ProfileView from './views/ProfileView'
import LandlordHousesView from './views/LandlordHousesView'
import AdminAuditView from './views/AdminAuditView'
import AdminChatsView from './views/AdminChatsView'
import AdminUsersView from './views/AdminUsersView'
import AdminReportsView from './views/AdminReportsView'
import AdminDashboardView from './views/AdminDashboardView'

/** 路由守卫：未登录踢回 /login，角色不符回各自首页 */
function Require({ roles, children }: { roles?: number[]; children: React.ReactNode }) {
  const auth = useAuth()
  if (!auth.isLogin) return <Navigate to="/login" replace />
  if (roles && !roles.includes(auth.role)) return <Navigate to={auth.home} replace />
  return <>{children}</>
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginView />} />
      <Route element={<NavLayout />}>
        <Route
          path="/app"
          element={
            <Require>
              <HomeView />
            </Require>
          }
        />
        <Route
          path="/app/houses/:id"
          element={
            <Require>
              <HouseDetailView />
            </Require>
          }
        />
        <Route
          path="/app/ai"
          element={
            <Require>
              <AiChatView />
            </Require>
          }
        />
        <Route
          path="/app/favorites"
          element={
            <Require>
              <FavoritesView />
            </Require>
          }
        />
        <Route
          path="/app/appointments"
          element={
            <Require>
              <AppointmentsView landlord={false} />
            </Require>
          }
        />
        <Route
          path="/app/contracts"
          element={
            <Require>
              <ContractsView />
            </Require>
          }
        />
        <Route
          path="/app/orders"
          element={
            <Require>
              <OrdersView />
            </Require>
          }
        />
        <Route
          path="/app/notifications"
          element={
            <Require>
              <NotificationsView />
            </Require>
          }
        />
        <Route
          path="/app/profile"
          element={
            <Require>
              <ProfileView />
            </Require>
          }
        />
        <Route
          path="/landlord/houses"
          element={
            <Require roles={[2]}>
              <LandlordHousesView />
            </Require>
          }
        />
        <Route
          path="/landlord/appointments"
          element={
            <Require roles={[2]}>
              <AppointmentsView landlord />
            </Require>
          }
        />
        <Route
          path="/landlord/contracts"
          element={
            <Require roles={[2]}>
              <ContractsView />
            </Require>
          }
        />
        <Route
          path="/landlord/orders"
          element={
            <Require roles={[2]}>
              <OrdersView />
            </Require>
          }
        />
        <Route
          path="/landlord/profile"
          element={
            <Require roles={[2]}>
              <ProfileView />
            </Require>
          }
        />
        <Route
          path="/admin/audit"
          element={
            <Require roles={[3]}>
              <AdminAuditView />
            </Require>
          }
        />
        <Route
          path="/admin/chats"
          element={
            <Require roles={[3]}>
              <AdminChatsView />
            </Require>
          }
        />
        <Route
          path="/admin/users"
          element={
            <Require roles={[3]}>
              <AdminUsersView />
            </Require>
          }
        />
        <Route
          path="/admin/reports"
          element={
            <Require roles={[3]}>
              <AdminReportsView />
            </Require>
          }
        />
        <Route
          path="/admin/dashboard"
          element={
            <Require roles={[3]}>
              <AdminDashboardView />
            </Require>
          }
        />
        <Route
          path="/admin/profile"
          element={
            <Require roles={[3]}>
              <ProfileView />
            </Require>
          }
        />
      </Route>
      <Route path="*" element={<Navigate to="/app" replace />} />
    </Routes>
  )
}
