const variants = {
  primary:
    'bg-plum text-ivory-card hover:bg-plum-light active:bg-plum-deep shadow-soft',
  coral:
    'bg-coral text-white hover:bg-coral-deep shadow-soft',
  outline:
    'bg-transparent text-plum border border-sand-line hover:bg-sand/60',
  ghost:
    'bg-transparent text-plum/70 hover:bg-sand/50 hover:text-plum',
}

const sizes = {
  sm: 'px-3 py-1.5 text-sm rounded-lg',
  md: 'px-4 py-2.5 text-sm rounded-xl',
  lg: 'px-6 py-3.5 text-base rounded-xl',
}

export default function Button({
  children,
  variant = 'primary',
  size = 'md',
  icon: Icon,
  className = '',
  ...props
}) {
  return (
    <button
      className={`inline-flex items-center justify-center gap-2 font-medium transition-colors duration-150 disabled:opacity-50 disabled:cursor-not-allowed ${variants[variant]} ${sizes[size]} ${className}`}
      {...props}
    >
      {Icon && <Icon size={16} strokeWidth={2} />}
      {children}
    </button>
  )
}
