/**
 * Facts about DevForge the project, as opposed to this deployment of it.
 *
 * These are constants rather than instance settings on purpose. An instance's
 * name, mark, and accent belong to whoever runs it — but the upstream project,
 * its licence, and where its source lives are the same wherever it is deployed.
 * A fork changes them here, once.
 */
export const PROJECT = {
  name: 'DevForge',
  repository: 'https://github.com/ensui-dev/devforge',
  license: 'MIT',
  licenseUrl: 'https://github.com/ensui-dev/devforge/blob/main/LICENSE',
  issues: 'https://github.com/ensui-dev/devforge/issues',
} as const

/** The command that turns "you could run this" into something to paste. */
export const CLONE_COMMAND = `git clone ${PROJECT.repository}.git`
