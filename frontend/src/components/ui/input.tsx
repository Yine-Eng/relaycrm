import * as React from 'react'

import { cn } from '@/lib/utils'

function Input({ className, type, ...props }: React.ComponentProps<'input'>) {
  return (
    <input
      type={type}
      data-slot="input"
      className={cn(
        'h-9 w-full min-w-0 rounded-lg border border-input bg-transparent px-3 py-1 text-sm text-foreground',
        'transition-[border-color,box-shadow] duration-150 ease-out outline-none',
        'selection:bg-primary selection:text-primary-foreground placeholder:text-subtle-foreground',
        'focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/40',
        'aria-invalid:border-destructive aria-invalid:ring-2 aria-invalid:ring-destructive/30',
        'disabled:cursor-not-allowed disabled:opacity-50',
        'file:inline-flex file:h-7 file:border-0 file:bg-transparent file:text-sm file:font-medium file:text-foreground',
        className,
      )}
      {...props}
    />
  )
}

export { Input }
