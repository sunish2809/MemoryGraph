import { Route, Routes } from 'react-router-dom'

import { AppLayout } from '@/components/layout/AppLayout'
import { RedirectIfAuthenticated, RequireAuth } from '@/components/routing/AuthGuards'
import { AskPage } from '@/features/ask/AskPage'
import { PrivacyPage } from '@/features/account/PrivacyPage'
import { LoginPage } from '@/features/auth/LoginPage'
import { RegisterPage } from '@/features/auth/RegisterPage'
import { DashboardPage } from '@/features/dashboard/DashboardPage'
import { ImportPage } from '@/features/imports/ImportPage'
import { GoogleOAuthCallbackPage } from '@/features/imports/GoogleOAuthCallbackPage'
import { MemoryDetailPage } from '@/features/memories/MemoryDetailPage'
import { FacesReviewPage } from '@/features/faces/FacesReviewPage'
import { GraphPage } from '@/features/people/GraphPage'
import { PeoplePage } from '@/features/people/PeoplePage'
import { PersonDetailPage } from '@/features/people/PersonDetailPage'
import { PlaceDetailPage } from '@/features/places/PlaceDetailPage'
import { PlacesPage } from '@/features/places/PlacesPage'
import { SearchPage } from '@/features/search/SearchPage'
import { NotFoundPage } from '@/features/system/NotFoundPage'
import { TimelinePage } from '@/features/timeline/TimelinePage'
import { TripDetailPage } from '@/features/trips/TripDetailPage'
import { TripsPage } from '@/features/trips/TripsPage'

export function App() {
  return (
    <Routes>
      <Route element={<RedirectIfAuthenticated />}>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
      </Route>

      <Route element={<RequireAuth />}>
        <Route element={<AppLayout />}>
          <Route index element={<DashboardPage />} />
          <Route path="/timeline" element={<TimelinePage />} />
          <Route path="/search" element={<SearchPage />} />
          <Route path="/people" element={<PeoplePage />} />
          <Route path="/people/:personId" element={<PersonDetailPage />} />
          <Route path="/faces" element={<FacesReviewPage />} />
          <Route path="/places" element={<PlacesPage />} />
          <Route path="/places/:placeId" element={<PlaceDetailPage />} />
          <Route path="/trips" element={<TripsPage />} />
          <Route path="/trips/:tripId" element={<TripDetailPage />} />
          <Route path="/graph" element={<GraphPage />} />
          <Route path="/import" element={<ImportPage />} />
          <Route path="/import/google/callback" element={<GoogleOAuthCallbackPage />} />
          <Route path="/memories/:memoryId" element={<MemoryDetailPage />} />
          <Route path="/ask" element={<AskPage />} />
          <Route path="/privacy" element={<PrivacyPage />} />
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
