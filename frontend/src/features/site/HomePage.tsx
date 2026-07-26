import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { handbookApi } from '../../shared/api/endpoints'
import { queryKeys } from '../../shared/api/queryKeys'
import { useAuth } from '../../shared/auth/useAuth'
import { useInstance } from '../../shared/instance/useInstance'
import { SiteChrome } from './SiteChrome'
import './HomePage.css'

/**
 * Feature cards, each naming the handbook page that explains it — so the copy
 * here stays short and the depth lives in documentation that can be edited
 * without touching this file.
 */
const FEATURES = [
  {
    title: 'Typed reference graph',
    slug: 'reference-graph',
    body: 'Links carry meaning — depends on, implements, supersedes — and every one is visible from both ends. Write the link once; the other page gets its backlink automatically.',
  },
  {
    title: 'Documentation with a shape',
    slug: 'document-types',
    body: 'Nine document types, from architecture and decision records to runbooks and API contracts. The type tells a reader what they are about to read.',
  },
  {
    title: 'Boards that cite knowledge',
    slug: 'tutorial-linking-tasks',
    body: 'A task points at the documents it depends on instead of restating them, and cards show those citations on the board itself.',
  },
  {
    title: 'Search that cannot drift',
    slug: 'tutorial-search',
    body: 'Full-text search across titles and bodies, ranked, maintained by PostgreSQL as part of each write. No index to rebuild, nothing to fall behind.',
  },
  {
    title: 'Teams and roles',
    slug: 'roles-and-permissions',
    body: 'Four ranked roles per workspace. A team always keeps an owner, nobody can grant authority above their own, and non-members cannot tell a workspace exists.',
  },
  {
    title: 'An API for all of it',
    slug: 'api-authentication',
    body: 'Every screen here is built on documented endpoints behind bearer tokens, with one error shape and per-field validation messages.',
  },
]

/** A real sequence — each step makes the next more useful — so the numbering means something. */
const STEPS = [
  {
    title: 'Write it down',
    slug: 'tutorial-writing-documents',
    body: 'Create a document in Markdown with a live preview beside it. Give it a type so readers know what kind of thing it is.',
  },
  {
    title: 'Link what depends on what',
    slug: 'tutorial-linking',
    body: 'Point the page that has a dependency at the one it relies on, and pick the relationship. The backlink appears on the far side by itself.',
  },
  {
    title: 'Track the work against it',
    slug: 'tutorial-boards',
    body: 'Run a board with work-in-progress limits, then attach the documents each task needs. Change a document and the graph shows every task that cited it.',
  },
]

export function HomePage() {
  const { isAuthenticated } = useAuth()

  const { instance, docsPath } = useInstance()

  // The configured handbook is a `handle/slug` pair.
  const [handbookHandle = '', handbookSlug = ''] = (instance.handbookPath ?? '').split('/')

  // Counts come from the live handbook, so the page never claims more than the
  // instance actually publishes.
  const { data: handbook } = useQuery({
    queryKey: queryKeys.handbook.contents(handbookHandle, handbookSlug),
    queryFn: () => handbookApi.contents(handbookHandle, handbookSlug),
    enabled: handbookHandle.length > 0 && handbookSlug.length > 0,
    staleTime: 5 * 60_000,
  })

  const pageCount = handbook?.entries.length ?? 0
  const showDocs = instance.publicDocsEnabled
  const canRegister = instance.registrationMode !== 'CLOSED'

  return (
    <SiteChrome>
      <section className="wrap hero">
        <div>
          <p className="mono-label hero__eyebrow">Documentation + delivery</p>
          <h1 className="hero__title">
            Know what breaks
            <br />
            before you change it.
          </h1>
          <p className="hero__lead">
            DevForge keeps what your team knows beside what it is doing, and links the two. Every
            link carries a meaning, and every one works in both directions — so the page you are
            about to edit already tells you what depends on it.
          </p>
          <div className="hero__actions">
            <Link
              className="cta cta--primary"
              to={isAuthenticated ? '/app' : canRegister ? '/register' : '/login'}
            >
              {isAuthenticated
                ? 'Open your workspaces'
                : canRegister
                  ? 'Create an account'
                  : 'Sign in'}
            </Link>
            {showDocs ? (
              <Link className="cta cta--secondary" to="/docs">
                Read the handbook
              </Link>
            ) : null}
          </div>
        </div>

        {/*
          The thesis shown rather than described: one authored link, and the
          backlink it produces on the other document. Built from the same parts
          the app draws in its Connections panel.
        */}
        <figure className="graph">
          <p className="mono-label graph__label">One link, two directions</p>
          <div className="graph__node">
            <span className="graph__type">ARCHITECTURE</span>
            <span className="graph__title">Event ingestion pipeline</span>
          </div>
          <div className="graph__link">
            <span className="graph__rel">DEPENDS_ON</span>
          </div>
          <div className="graph__node graph__node--target">
            <span className="graph__type">TECHNOLOGY</span>
            <span className="graph__title">Kafka topic conventions</span>
            <div className="graph__backlink">
              <span className="graph__backlink-label">Referenced by · appears automatically</span>
              <p className="graph__backlink-body">
                Required by <strong>Event ingestion pipeline</strong>
              </p>
            </div>
          </div>
          <figcaption className="graph__caption">
            You write the link once, from the page that has the dependency. The other page gets
            its backlink without anyone maintaining it.
          </figcaption>
        </figure>
      </section>

      <section className="wrap band">
        <div className="band__head">
          <p className="mono-label">The problem</p>
          <h2 className="band__title">
            Documentation rots because nobody knows what a change affects.
          </h2>
          <p className="band__lead">
            You edit the authentication design. Three runbooks, two decision records, and a service
            README quietly become wrong. Nothing tells you, because a plain hyperlink carries no
            meaning and points only one way.
          </p>
        </div>
        <div className="problems">
          <div className="problem">
            <h3 className="problem__title">A wiki tells you what was written</h3>
            <p className="problem__body">Not which of it still holds, and not what relies on it.</p>
          </div>
          <div className="problem">
            <h3 className="problem__title">A board tells you what is being done</h3>
            <p className="problem__body">
              With the context copied into a description that goes stale the moment the source
              changes.
            </p>
          </div>
          <div className="problem">
            <h3 className="problem__title">Neither answers the real question</h3>
            <p className="problem__body">
              “If I change this, what else has to change?” — the question that actually costs teams
              their week.
            </p>
          </div>
        </div>
      </section>

      <section className="wrap band" id="features">
        <div className="band__head">
          <p className="mono-label">Features</p>
          <h2 className="band__title">Built around one idea, carried through everything.</h2>
        </div>
        <div className="cards">
          {FEATURES.map((feature) => (
            <article className="feature" key={feature.slug}>
              <h3 className="feature__title">{feature.title}</h3>
              <p className="feature__body">{feature.body}</p>
              {showDocs ? (
                <Link className="feature__link" to={docsPath(feature.slug)}>
                  Read the page →
                </Link>
              ) : null}
            </article>
          ))}
        </div>
      </section>

      <section className="wrap band" id="how">
        <div className="band__head">
          <p className="mono-label">How it works</p>
          <h2 className="band__title">Three habits, in order.</h2>
          <p className="band__lead">
            Each step is worth something on its own, and each one makes the next more useful.
          </p>
        </div>
        <div className="steps">
          {STEPS.map((step, index) => (
            <div className="step" key={step.slug}>
              <p className="step__n">Step {index + 1}</p>
              <h3 className="step__title">{step.title}</h3>
              <p className="step__body">{step.body}</p>
              {showDocs ? (
                <Link className="step__link" to={docsPath(step.slug)}>
                  Read the tutorial →
                </Link>
              ) : null}
            </div>
          ))}
        </div>
      </section>

      {showDocs ? (
        <section className="wrap band" id="handbook">
          <div className="band__head">
            <p className="mono-label">The handbook</p>
            <h2 className="band__title">DevForge documents itself, in DevForge.</h2>
            <p className="band__lead">
              {pageCount > 0
                ? `The handbook is a real workspace on ${instance.name} — ${pageCount} pages joined by typed links, with a board whose tasks cite the pages that explain them. The documentation you read is served straight from it, so it cannot drift from the product.`
                : 'The handbook is a real workspace on this instance, served straight from the API. Seed it with scripts/seed_handbook.py and it appears here.'}
            </p>
          </div>
          <Link className="cta cta--primary" to="/docs">
            Open the handbook
          </Link>
        </section>
      ) : null}
    </SiteChrome>
  )
}
