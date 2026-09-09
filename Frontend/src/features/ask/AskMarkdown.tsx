import { Fragment, type ReactNode } from 'react'

/**
 * Renders Ask answers as headings, lists, and bold — without treating the model text as HTML.
 */
export function AskMarkdown({
  text,
  onCite,
}: {
  text: string
  onCite?: ((index: number) => void) | undefined
}) {
  const blocks = parseBlocks(text)
  return (
    <div className="flex flex-col gap-3 text-[0.95rem] leading-relaxed text-fg">
      {blocks.map((block, index) => (
        <BlockView key={index} block={block} onCite={onCite} />
      ))}
    </div>
  )
}

type Block =
  | { type: 'heading'; level: 1 | 2 | 3; text: string }
  | { type: 'paragraph'; text: string }
  | { type: 'list'; ordered: boolean; items: ListItem[] }

type ListItem = { text: string; children?: { ordered: boolean; items: ListItem[] } }

function BlockView({
  block,
  onCite,
}: {
  block: Block
  onCite?: ((index: number) => void) | undefined
}) {
  if (block.type === 'heading') {
    const Tag = block.level === 1 ? 'h3' : block.level === 2 ? 'h4' : 'h5'
    const size = block.level === 1 ? 'text-lg' : block.level === 2 ? 'text-base' : 'text-sm'
    return (
      <Tag className={`font-display font-semibold tracking-tight text-fg ${size}`}>
        {inline(block.text, onCite)}
      </Tag>
    )
  }
  if (block.type === 'paragraph') {
    return <p className="text-fg">{inline(block.text, onCite)}</p>
  }
  return <ListView ordered={block.ordered} items={block.items} onCite={onCite} />
}

function ListView({
  ordered,
  items,
  onCite,
}: {
  ordered: boolean
  items: ListItem[]
  onCite?: ((index: number) => void) | undefined
}) {
  const Tag = ordered ? 'ol' : 'ul'
  return (
    <Tag
      className={
        ordered
          ? 'flex list-decimal flex-col gap-2 pl-5 marker:font-medium marker:text-accent'
          : 'flex list-disc flex-col gap-1.5 pl-5 marker:text-accent'
      }
    >
      {items.map((item, index) => (
        <li key={index} className="pl-1">
          <span>{inline(item.text, onCite)}</span>
          {item.children && (
            <div className="mt-1.5">
              <ListView ordered={item.children.ordered} items={item.children.items} onCite={onCite} />
            </div>
          )}
        </li>
      ))}
    </Tag>
  )
}

function parseBlocks(text: string): Block[] {
  const lines = text.replace(/\r\n/g, '\n').split('\n')
  const blocks: Block[] = []
  let i = 0
  while (i < lines.length) {
    const line = lines[i]
    if (line.trim() === '') {
      i += 1
      continue
    }
    const heading = line.match(/^(#{1,3})\s+(.*)$/)
    if (heading) {
      blocks.push({
        type: 'heading',
        level: heading[1].length as 1 | 2 | 3,
        text: heading[2].trim(),
      })
      i += 1
      continue
    }
    if (isListLine(line)) {
      const { block, next } = parseList(lines, i, indentOf(line))
      blocks.push(block)
      i = next
      continue
    }
    const para: string[] = [line.trim()]
    i += 1
    while (i < lines.length && lines[i].trim() !== '' && !isListLine(lines[i]) && !lines[i].startsWith('#')) {
      para.push(lines[i].trim())
      i += 1
    }
    blocks.push({ type: 'paragraph', text: para.join(' ') })
  }
  return blocks.length > 0 ? blocks : [{ type: 'paragraph', text: text.trim() }]
}

function parseList(
  lines: string[],
  start: number,
  baseIndent: number,
): { block: Extract<Block, { type: 'list' }>; next: number } {
  const ordered = orderedMatch(lines[start]) != null
  const items: ListItem[] = []
  let i = start
  while (i < lines.length) {
    const line = lines[i]
    if (line.trim() === '') {
      if (i + 1 < lines.length && indentOf(lines[i + 1]) > baseIndent) {
        i += 1
        continue
      }
      break
    }
    const indent = indentOf(line)
    if (indent < baseIndent) break
    if (indent > baseIndent && isListLine(line) && items.length > 0) {
      const nested = parseList(lines, i, indent)
      const last = items[items.length - 1]
      last.children = { ordered: nested.block.ordered, items: nested.block.items }
      i = nested.next
      continue
    }
    if (!isListLine(line)) {
      if (indent > baseIndent && items.length > 0) {
        items[items.length - 1].text += ' ' + line.trim()
        i += 1
        continue
      }
      break
    }
    const thisOrdered = orderedMatch(line) != null
    if (thisOrdered !== ordered && indent === baseIndent) break
    items.push({ text: listText(line) })
    i += 1
  }
  return { block: { type: 'list', ordered, items }, next: i }
}

function isListLine(line: string): boolean {
  return unorderedMatch(line) != null || orderedMatch(line) != null
}

function unorderedMatch(line: string) {
  return line.match(/^(\s*)[-*]\s+(.*)$/)
}

function orderedMatch(line: string) {
  return line.match(/^(\s*)\d+\.\s+(.*)$/)
}

function listText(line: string): string {
  return (unorderedMatch(line)?.[2] ?? orderedMatch(line)?.[2] ?? line).trim()
}

function indentOf(line: string): number {
  const match = line.match(/^(\s*)/)
  return match ? match[1].length : 0
}

function inline(text: string, onCite?: ((index: number) => void) | undefined): ReactNode[] {
  const nodes: ReactNode[] = []
  const pattern = /(\*\*(.+?)\*\*|\[(\d+)\])/g
  let last = 0
  let match: RegExpExecArray | null
  let key = 0
  while ((match = pattern.exec(text)) !== null) {
    if (match.index > last) {
      nodes.push(<Fragment key={key++}>{text.slice(last, match.index)}</Fragment>)
    }
    if (match[2] != null) {
      nodes.push(
        <strong key={key++} className="font-semibold text-fg">
          {match[2]}
        </strong>,
      )
    } else if (match[3] != null) {
      const index = Number(match[3])
      nodes.push(
        <button
          key={key++}
          type="button"
          onClick={() => onCite?.(index)}
          className="mx-0.5 inline-flex min-w-5 items-center justify-center rounded-md bg-accent-muted px-1 text-[11px] font-semibold text-accent hover:bg-accent/20"
        >
          {index}
        </button>,
      )
    }
    last = match.index + match[0].length
  }
  if (last < text.length) {
    nodes.push(<Fragment key={key++}>{text.slice(last)}</Fragment>)
  }
  return nodes
}
