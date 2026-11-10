import { createContext } from 'react';

export type ToastTone = 'error' | 'info';

export interface ToastMessage {
  tone?: ToastTone;
  title: string;
  detail?: string | null;
}

export interface ToastContextValue {
  showToast: (message: ToastMessage) => void;
}

export const ToastContext = createContext<ToastContextValue | null>(null);
