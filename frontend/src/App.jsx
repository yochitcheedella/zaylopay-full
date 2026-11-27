import './App.css';
import { lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { MotionProvider } from './motion';
import Home from './Pages/Home/Home';
import About from './Pages/About/About';
import HowItWorksPage from './Pages/Howitworkspage/Howitworkspage';
import Membership from './Pages/Membership/Membership';
import PublicLayout, { NotFound, PageErrorBoundary, PageLoading, RouteMetadata } from './Components/PublicLayout/PublicLayout';
import ScrollManager from './Components/PublicLayout/ScrollManager';
const Login = lazy(() => import('./Pages/Login/Login'));
const Register = lazy(() => import('./Pages/Register/Register'));
const MemberWelcomePage = lazy(() => import('./Pages/Welcome/MemberWelcomePage'));
const AdminDashboard = lazy(() => import('./Pages/Admin/AdminDashboard'));
function AccountPage({ children }) {
  return <PageErrorBoundary><Suspense fallback={<PageLoading />}>{children}</Suspense></PageErrorBoundary>;
}
function RegisterRoute() {
  return <AccountPage><Register /></AccountPage>;
}
export default function App() {
  return <MotionProvider><BrowserRouter><ScrollManager /><RouteMetadata /><Routes>
    <Route element={<PublicLayout />}>
      <Route index element={<Home />} />
      <Route path="about" element={<About />} />
      <Route path="how-it-works" element={<HowItWorksPage />} />
      <Route path="membership" element={<Membership />} />
      <Route path="*" element={<NotFound />} />
    </Route>
    <Route path="/login" element={<AccountPage><Login /></AccountPage>} />
    <Route path="/register" element={<RegisterRoute />} />
    <Route path="/register/review" element={<Navigate to="/register?step=3" replace />} />
    <Route path="/review" element={<Navigate to="/register?step=3" replace />} />
    <Route path="/welcome" element={<AccountPage><MemberWelcomePage /></AccountPage>} />
    <Route path="/dashboard" element={<AccountPage><MemberWelcomePage /></AccountPage>} />
    <Route path="/admin" element={<AccountPage><AdminDashboard /></AccountPage>} />
    <Route path="/admin/approvals" element={<AccountPage><AdminDashboard /></AccountPage>} />
  </Routes></BrowserRouter></MotionProvider>;
}
