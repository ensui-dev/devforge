/**
 * Substitutes instance variables in document bodies.
 *
 * Self-hosted documentation has a problem a hosted product does not: a page that
 * says "open http://localhost:3000" is wrong on every deployment except the one
 * it was written on. Writing the address into the stored content only moves the
 * problem — it makes the content instance-specific, so the same handbook cannot
 * be seeded onto two instances.
 *
 * So the address is written as a variable and resolved when the page renders.
 *
 * | Variable             | Resolves to                                 |
 * |----------------------|---------------------------------------------|
 * | `{{instance.url}}`   | the origin serving the page                 |
 * | `{{instance.name}}`  | this instance's configured name             |
 * | `{{handbook.pages}}` | how many pages this documentation site has  |
 *
 * The page count is here for the same reason the address is. A handbook that
 * says "thirty pages" is wrong the moment someone writes the thirty-first, and
 * nobody remembers to go back and change the sentence — a number written into
 * prose is a number that will be a lie eventually.
 *
 * Deliberately tiny. This is a documentation convenience, not a template
 * language: there are no conditionals, no loops, and no way to reach anything
 * that is not already public. Unknown `{{…}}` sequences are left exactly as
 * written, so a document that happens to contain braces is unaffected.
 */

export interface InstanceVariables {
  /** The origin this page is served from — the instance it is actually on. */
  url: string
  name: string
  /**
   * Pages in the documentation site this page belongs to.
   *
   * Absent where the count is not known — a document read inside the app is not
   * being read as part of a site — and the variable is then left visible rather
   * than resolved to a guess.
   */
  pages?: number
}

/** Matches `{{ namespace.thing }}`, tolerating surrounding whitespace. */
const VARIABLE = /\{\{\s*([a-z]+)\.([a-z]+)\s*\}\}/g

export function applyInstanceVariables(content: string, variables: InstanceVariables): string {
  if (!content || !content.includes('{{')) {
    return content
  }

  return content.replace(VARIABLE, (whole, namespace: string, key: string) => {
    if (namespace === 'instance' && key === 'url') {
      // Trailing slashes would produce `https://host//docs` when a document
      // writes `{{instance.url}}/docs`.
      return variables.url.replace(/\/+$/, '')
    }
    if (namespace === 'instance' && key === 'name') {
      return variables.name
    }
    if (namespace === 'handbook' && key === 'pages' && variables.pages !== undefined) {
      return String(variables.pages)
    }
    // An unrecognised variable stays visible rather than vanishing, so a typo is
    // obvious to whoever wrote the page instead of silently producing an empty
    // link — and so is a count asked for where none is known.
    return whole
  })
}
