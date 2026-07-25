import type { DragEvent } from 'react'
import { Badge } from '../../shared/components/Badge'
import type { Task, TaskPriority } from '../../shared/types'
import { initials } from '../../shared/utils/slugify'
import './TaskCard.css'

/** Only HIGH and CRITICAL earn the signal colour; the rest stay quiet. */
const PRIORITY_TONE: Record<TaskPriority, 'neutral' | 'signal' | 'danger'> = {
  LOW: 'neutral',
  MEDIUM: 'neutral',
  HIGH: 'signal',
  CRITICAL: 'danger',
}

interface TaskCardProps {
  task: Task
  draggable: boolean
  onOpen: () => void
  onDragStart: (event: DragEvent<HTMLElement>) => void
  onDragEnd: () => void
  dragging: boolean
}

export function TaskCard({
  task,
  draggable,
  onOpen,
  onDragStart,
  onDragEnd,
  dragging,
}: TaskCardProps) {
  return (
    <article
      className={dragging ? 'task-card task-card--dragging' : 'task-card'}
      draggable={draggable}
      onDragStart={onDragStart}
      onDragEnd={onDragEnd}
    >
      {/* The whole card is not a button: it holds links, so a nested interactive
          element would be invalid. The title is the actionable control. */}
      <button type="button" className="task-card__title" onClick={onOpen}>
        {task.title}
      </button>

      <div className="task-card__tags">
        {task.priority !== 'MEDIUM' ? (
          <Badge tone={PRIORITY_TONE[task.priority]}>{task.priority}</Badge>
        ) : null}
        {task.linkedDocuments.length > 0 ? (
          <Badge tone="trace" title={task.linkedDocuments.map((doc) => doc.title).join(', ')}>
            {task.linkedDocuments.length} doc{task.linkedDocuments.length === 1 ? '' : 's'}
          </Badge>
        ) : null}
      </div>

      {/* A stub of the reference trace, so a card carrying documentation is
          recognisable at board level without opening it. */}
      {task.linkedDocuments.length > 0 ? (
        <ul className="task-card__docs">
          {task.linkedDocuments.slice(0, 2).map((document) => (
            <li key={document.id} className="task-card__doc">
              {document.title}
            </li>
          ))}
          {task.linkedDocuments.length > 2 ? (
            <li className="task-card__doc task-card__doc--more">
              +{task.linkedDocuments.length - 2} more
            </li>
          ) : null}
        </ul>
      ) : null}

      {task.assignee ? (
        <div className="task-card__assignee" title={task.assignee.email}>
          <span className="task-card__avatar" aria-hidden="true">
            {initials(task.assignee.displayName)}
          </span>
          <span className="task-card__assignee-name">{task.assignee.displayName}</span>
        </div>
      ) : null}
    </article>
  )
}
