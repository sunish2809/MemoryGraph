import { useLocation, useNavigate } from 'react-router-dom'

import { Button } from '@/components/ui/Button'

/**
 * In-app back: previous screen when this tab has history, otherwise Dashboard.
 * Avoids sending people to the browser chrome to leave a memory or Ask thread.
 */
export function BackButton() {
  const navigate = useNavigate()
  const location = useLocation()

  return (
    <Button
      type="button"
      variant="ghost"
      className="!px-2 !py-1.5 text-xs"
      onClick={() => {
        if (location.key !== 'default') {
          navigate(-1)
          return
        }
        navigate('/dashboard')
      }}
    >
      Back
    </Button>
  )
}
