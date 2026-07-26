/**
 * Line-level diff between two document bodies.
 *
 * Implemented here rather than pulled in: the whole algorithm is under a hundred
 * lines, and a self-hosted instance should not gain a dependency to compare two
 * strings.
 *
 * The shape is a longest-common-subsequence over lines, which is what `git diff`
 * computes (git uses Myers, an LCS algorithm chosen for its memory behaviour on
 * large inputs). Common prefix and suffix are stripped first — an edit to one
 * paragraph of a long page reduces to a handful of lines, so the quadratic part
 * almost never runs on the whole document.
 */

export type DiffKind = 'context' | 'removed' | 'added'

export interface DiffLine {
  kind: DiffKind
  text: string
  /** 1-based line number in the old text, absent for added lines. */
  before?: number
  /** 1-based line number in the new text, absent for removed lines. */
  after?: number
}

export interface DiffStats {
  added: number
  removed: number
  /** True when the two texts are identical, so the UI can say so plainly. */
  unchanged: boolean
}

export interface Diff {
  lines: DiffLine[]
  stats: DiffStats
}

/**
 * Above this many lines on either side, the quadratic table is not worth
 * building. Real documentation does not reach it; a pasted data file might, and
 * locking the tab up would be worse than a coarse answer.
 */
const MAX_LCS_LINES = 3000

export function diffLines(before: string, after: string): Diff {
  const beforeLines = split(before)
  const afterLines = split(after)

  if (before === after) {
    return {
      lines: beforeLines.map((text, i) => ({
        kind: 'context' as const,
        text,
        before: i + 1,
        after: i + 1,
      })),
      stats: { added: 0, removed: 0, unchanged: true },
    }
  }

  // Strip the matching head and tail, which is where most of a document lives.
  let head = 0
  while (
    head < beforeLines.length &&
    head < afterLines.length &&
    beforeLines[head] === afterLines[head]
  ) {
    head++
  }

  let tail = 0
  while (
    tail < beforeLines.length - head &&
    tail < afterLines.length - head &&
    beforeLines[beforeLines.length - 1 - tail] === afterLines[afterLines.length - 1 - tail]
  ) {
    tail++
  }

  const beforeMiddle = beforeLines.slice(head, beforeLines.length - tail)
  const afterMiddle = afterLines.slice(head, afterLines.length - tail)

  const middle =
    beforeMiddle.length > MAX_LCS_LINES || afterMiddle.length > MAX_LCS_LINES
      ? // Too big to align properly: report the block as replaced wholesale. Still
        // truthful, just less precise about which lines within it moved.
        [
          ...beforeMiddle.map((text, i) => ({ kind: 'removed' as const, text, before: head + i + 1 })),
          ...afterMiddle.map((text, i) => ({ kind: 'added' as const, text, after: head + i + 1 })),
        ]
      : alignByLcs(beforeMiddle, afterMiddle, head)

  const lines: DiffLine[] = [
    ...beforeLines.slice(0, head).map((text, i) => ({
      kind: 'context' as const,
      text,
      before: i + 1,
      after: i + 1,
    })),
    ...middle,
    ...beforeLines.slice(beforeLines.length - tail).map((text, i) => ({
      kind: 'context' as const,
      text,
      before: beforeLines.length - tail + i + 1,
      after: afterLines.length - tail + i + 1,
    })),
  ]

  return {
    lines,
    stats: {
      added: lines.filter((line) => line.kind === 'added').length,
      removed: lines.filter((line) => line.kind === 'removed').length,
      unchanged: false,
    },
  }
}

/** Splits into lines without inventing a trailing empty one. */
function split(text: string): string[] {
  if (text === '') {
    return []
  }
  return text.replace(/\r\n/g, '\n').split('\n')
}

/**
 * Walks an LCS table to produce the interleaved diff.
 *
 * @param offset how many lines were stripped from the head, so reported line
 *               numbers refer to the whole document rather than the middle
 */
function alignByLcs(before: string[], after: string[], offset: number): DiffLine[] {
  const rows = before.length
  const cols = after.length

  // (rows+1) x (cols+1) of common-subsequence lengths, flattened.
  const table = new Int32Array((rows + 1) * (cols + 1))
  const at = (r: number, c: number) => r * (cols + 1) + c

  for (let r = rows - 1; r >= 0; r--) {
    for (let c = cols - 1; c >= 0; c--) {
      table[at(r, c)] =
        before[r] === after[c]
          ? table[at(r + 1, c + 1)] + 1
          : Math.max(table[at(r + 1, c)], table[at(r, c + 1)])
    }
  }

  const lines: DiffLine[] = []
  let r = 0
  let c = 0

  while (r < rows && c < cols) {
    if (before[r] === after[c]) {
      lines.push({ kind: 'context', text: before[r], before: offset + r + 1, after: offset + c + 1 })
      r++
      c++
    } else if (table[at(r + 1, c)] >= table[at(r, c + 1)]) {
      // Removals before additions, so a replaced line reads as "- old / + new".
      lines.push({ kind: 'removed', text: before[r], before: offset + r + 1 })
      r++
    } else {
      lines.push({ kind: 'added', text: after[c], after: offset + c + 1 })
      c++
    }
  }

  while (r < rows) {
    lines.push({ kind: 'removed', text: before[r], before: offset + r + 1 })
    r++
  }
  while (c < cols) {
    lines.push({ kind: 'added', text: after[c], after: offset + c + 1 })
    c++
  }

  return lines
}
