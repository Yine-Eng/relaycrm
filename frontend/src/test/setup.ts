import '@testing-library/jest-dom/vitest'

import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// jsdom lacks a few browser APIs that Radix menus and dialogs use.
if (!('ResizeObserver' in globalThis)) {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
}
Element.prototype.scrollIntoView ??= function scrollIntoView() {}
Element.prototype.hasPointerCapture ??= () => false
Element.prototype.releasePointerCapture ??= function releasePointerCapture() {}

if (!('matchMedia' in window)) {
  Object.defineProperty(window, 'matchMedia', {
    value: (query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      addListener: () => undefined,
      removeListener: () => undefined,
      dispatchEvent: () => false,
    }),
  })
}

afterEach(() => {
  cleanup()
  localStorage.clear()
  delete document.documentElement.dataset.theme
})
