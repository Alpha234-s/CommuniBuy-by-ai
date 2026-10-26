/// <reference types="vite/client" />

declare const __SINGLE_FILE__: boolean

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  readonly VITE_ROUTER_MODE?: string
}
