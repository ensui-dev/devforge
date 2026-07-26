import { useContext } from 'react'
import { InstanceContext } from './InstanceContext'
import type { InstanceContextValue } from './InstanceContext'

export function useInstance(): InstanceContextValue {
  const value = useContext(InstanceContext)
  if (!value) {
    throw new Error('useInstance must be used inside an InstanceProvider')
  }
  return value
}
