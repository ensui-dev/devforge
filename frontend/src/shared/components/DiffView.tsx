import { useMemo } from 'react'
import { diffLines } from '../utils/diffLines'
import './DiffView.css'

interface DiffViewProps {
  /** What it said before — the left of the comparison. */
  before: string
  /** What it says now. */
  after: string
  /** Names what is being compared, for anyone not reading the colours. */
  label: string
  /** Shown in place of the diff when the two are identical. */
  unchangedNote?: string
}

/**
 * Two texts, with what changed between them.
 *
 * Shared rather than written twice: history compares a revision against the live
 * document, and a connection compares what a linked page said when this one was
 * last written against what it says now. Different questions, the same rendering —
 * and a diff whose two implementations disagreed about what counts as a change
 * would be worse than either.
 *
 * Every line carries `+` or `−` as well as its colour, so the diff still reads
 * when printed, read aloud, or seen by someone who cannot distinguish the two
 * washes.
 */
export function DiffView({ before, after, label, unchangedNote }: DiffViewProps) {
  const diff = useMemo(() => diffLines(before, after), [before, after])

  if (diff.stats.unchanged) {
    return unchangedNote ? <p className="diff__unchanged">{unchangedNote}</p> : null
  }

  return (
    <div className="diff" role="group" aria-label={label}>
      {diff.lines.map((line, index) => (
        <div className={`diff__line diff__line--${line.kind}`} key={index}>
          <span className="diff__gutter" aria-hidden="true">
            {line.before ?? ''}
          </span>
          <span className="diff__gutter" aria-hidden="true">
            {line.after ?? ''}
          </span>
          <span className="diff__marker" aria-hidden="true">
            {line.kind === 'added' ? '+' : line.kind === 'removed' ? '−' : ' '}
          </span>
          <span className="diff__text">{line.text || ' '}</span>
        </div>
      ))}
    </div>
  )
}
