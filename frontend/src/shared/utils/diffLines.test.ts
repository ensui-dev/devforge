import { describe, expect, it } from 'vitest'
import { diffLines } from './diffLines'

/** Compact rendering, so a test reads like the diff it asserts. */
function render(before: string, after: string): string {
  return diffLines(before, after)
    .lines.map((line) => {
      const marker = line.kind === 'added' ? '+' : line.kind === 'removed' ? '-' : ' '
      return `${marker}${line.text}`
    })
    .join('\n')
}

describe('diffLines', () => {
  it('reports an unchanged document as unchanged rather than as a whole-file rewrite', () => {
    const text = 'one\ntwo\nthree'
    const diff = diffLines(text, text)

    expect(diff.stats).toEqual({ added: 0, removed: 0, unchanged: true })
    expect(diff.lines.every((line) => line.kind === 'context')).toBe(true)
  })

  it('shows a replaced line as a removal followed by an addition', () => {
    expect(render('one\ntwo\nthree', 'one\nTWO\nthree')).toBe(
      [' one', '-two', '+TWO', ' three'].join('\n'),
    )
  })

  it('shows an inserted line without disturbing its neighbours', () => {
    expect(render('one\nthree', 'one\ntwo\nthree')).toBe(
      [' one', '+two', ' three'].join('\n'),
    )
    expect(diffLines('one\nthree', 'one\ntwo\nthree').stats).toMatchObject({
      added: 1,
      removed: 0,
    })
  })

  it('shows a deleted line', () => {
    expect(render('one\ntwo\nthree', 'one\nthree')).toBe(
      [' one', '-two', ' three'].join('\n'),
    )
  })

  /**
   * The property that makes the diff readable: an edit in the middle of a long
   * page must not report the whole page as changed.
   */
  it('keeps an edit local instead of realigning the whole document', () => {
    const before = ['a', 'b', 'c', 'target', 'e', 'f', 'g'].join('\n')
    const after = ['a', 'b', 'c', 'changed', 'e', 'f', 'g'].join('\n')

    expect(diffLines(before, after).stats).toMatchObject({ added: 1, removed: 1 })
  })

  it('numbers lines against each side, so both gutters are correct', () => {
    const diff = diffLines('one\ntwo\nthree', 'one\nthree')

    expect(diff.lines).toEqual([
      { kind: 'context', text: 'one', before: 1, after: 1 },
      { kind: 'removed', text: 'two', before: 2 },
      { kind: 'context', text: 'three', before: 3, after: 2 },
    ])
  })

  it('handles a document that was empty', () => {
    expect(render('', 'first line')).toBe('+first line')
    expect(diffLines('', 'first line').stats).toMatchObject({ added: 1, removed: 0 })
  })

  it('handles a document that became empty', () => {
    expect(render('was here', '')).toBe('-was here')
  })

  it('treats both empty as unchanged', () => {
    expect(diffLines('', '').stats.unchanged).toBe(true)
  })

  /** A CRLF paste must not read as every line having changed. */
  it('ignores line-ending style', () => {
    expect(diffLines('one\r\ntwo', 'one\ntwo').stats).toMatchObject({
      added: 0,
      removed: 0,
    })
  })

  it('does not invent a trailing blank line', () => {
    expect(diffLines('one\n', 'one\n').lines).toHaveLength(2)
    expect(diffLines('one', 'one').lines).toHaveLength(1)
  })

  it('reports a wholesale rewrite as such', () => {
    const diff = diffLines('alpha\nbeta', 'gamma\ndelta')
    expect(diff.stats).toMatchObject({ added: 2, removed: 2 })
  })

  /**
   * Above the alignment ceiling the diff degrades to "this block was replaced",
   * which is coarser but still truthful — and does not lock the tab up building a
   * multi-million-cell table.
   */
  it('degrades gracefully on inputs too large to align', () => {
    const before = Array.from({ length: 3500 }, (_, i) => `line ${i}`).join('\n')
    const after = Array.from({ length: 3500 }, (_, i) => `changed ${i}`).join('\n')

    const started = Date.now()
    const diff = diffLines(before, after)

    expect(diff.stats.added).toBe(3500)
    expect(diff.stats.removed).toBe(3500)
    expect(Date.now() - started).toBeLessThan(2000)
  })

  /** A large document with one edit still aligns, because head/tail are stripped. */
  it('aligns a one-line edit in a very long document', () => {
    const lines = Array.from({ length: 5000 }, (_, i) => `line ${i}`)
    const before = lines.join('\n')
    const edited = [...lines]
    edited[2500] = 'edited'
    const after = edited.join('\n')

    const diff = diffLines(before, after)

    expect(diff.stats).toMatchObject({ added: 1, removed: 1 })
    expect(diff.lines.find((line) => line.kind === 'added')?.after).toBe(2501)
  })
})
