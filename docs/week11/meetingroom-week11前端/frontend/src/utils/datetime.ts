export function pad2(value: number): string {
  return String(value).padStart(2, '0')
}

export function toDateString(date: Date = new Date()): string {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`
}

export function todayString(): string {
  return toDateString(new Date())
}

export function tomorrowString(): string {
  const date = new Date()
  date.setDate(date.getDate() + 1)
  return toDateString(date)
}

export function parseLocalDateTime(value: string): Date | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?/.exec(value)
  if (!match) return null

  const date = new Date(
    Number(match[1]),
    Number(match[2]) - 1,
    Number(match[3]),
    Number(match[4]),
    Number(match[5]),
    Number(match[6] ?? 0)
  )

  return Number.isNaN(date.getTime()) ? null : date
}

export function formatTime(value: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/.exec(value)
  return match ? `${match[4]}:${match[5]}` : value
}

export function formatDateTime(value: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/.exec(value)
  return match ? `${match[1]}-${match[2]}-${match[3]} ${match[4]}:${match[5]}` : value
}

export function formatDateLabel(date: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(date)
  return match ? `${match[1]}年${Number(match[2])}月${Number(match[3])}日` : date
}

/** datetime-local 的 YYYY-MM-DDTHH:mm 转成本地时间字符串（补秒，不改时区） */
export function normalizeDateTime(value: string): string {
  if (!value) return ''
  const withSeconds = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value)
    ? `${value}:00`
    : value
  return withSeconds.replace(' ', 'T')
}

export function diffMinutes(start: string, end: string): number | null {
  const startDate = parseLocalDateTime(start)
  const endDate = parseLocalDateTime(end)
  if (!startDate || !endDate) return null
  return (endDate.getTime() - startDate.getTime()) / 60_000
}

/** 距开始还有多少分钟；已开始返回负数。仅用于操作按钮的可用性提示，最终以后端校验为准。 */
export function minutesUntilStart(value: string): number | null {
  const startDate = parseLocalDateTime(value)
  if (!startDate) return null
  return Math.floor((startDate.getTime() - Date.now()) / 60_000)
}

export function isSameLocalDate(start: string, end: string): boolean {
  const startDate = parseLocalDateTime(start)
  const endDate = parseLocalDateTime(end)
  if (!startDate || !endDate) return false

  return (
    startDate.getFullYear() === endDate.getFullYear() &&
    startDate.getMonth() === endDate.getMonth() &&
    startDate.getDate() === endDate.getDate()
  )
}

export function combineDateTime(date: string, time: string): string {
  return date && time ? `${date}T${time}` : ''
}
