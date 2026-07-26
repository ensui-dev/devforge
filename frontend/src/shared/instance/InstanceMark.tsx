import type { CSSProperties } from 'react'
import './InstanceMark.css'

interface InstanceMarkProps {
  name: string
  logoMark: string | null
  logoImage: string | null
  /** Only passed by the setup preview, which shows a colour not yet applied. */
  accentColor?: string
}

/**
 * This instance's name beside its mark.
 *
 * An uploaded image wins over the typed mark, since an operator who supplied one
 * meant it to be used. Both are optional: a deployment with neither still reads
 * as itself, just without a glyph.
 */
export function InstanceMark({ name, logoMark, logoImage, accentColor }: InstanceMarkProps) {
  return (
    <span className="imark" style={accentColor ? ({ '--imark-accent': accentColor } as CSSProperties) : undefined}>
      {logoImage ? (
        <img className="imark__image" src={logoImage} alt="" />
      ) : logoMark ? (
        <span className="imark__glyph" aria-hidden="true">
          {logoMark}
        </span>
      ) : null}
      <span className="imark__name">{name}</span>
    </span>
  )
}
