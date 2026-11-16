import type { StatisticsRange } from '../api/statistics'

export type DatePreset = 'today' | 'yesterday' | '7' | '30' | 'custom'
const DAY = 86400000
export function shanghaiToday(now = new Date()) {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(now)
  const part = (type: string) => parts.find((item) => item.type === type)!.value
  return `${part('year')}-${part('month')}-${part('day')}`
}
export function shiftDate(date: string, days: number) {
  return new Date(Date.parse(`${date}T00:00:00Z`) + days * DAY).toISOString().slice(0, 10)
}
export function presetRange(
  preset: Exclude<DatePreset, 'custom'>,
  today = shanghaiToday(),
): StatisticsRange {
  const endDate = preset === 'yesterday' ? shiftDate(today, -1) : today
  return { startDate: shiftDate(endDate, preset === '7' ? -6 : preset === '30' ? -29 : 0), endDate }
}
export function validateRange(range: StatisticsRange) {
  const valid = (value: string) =>
    /^\d{4}-\d{2}-\d{2}$/.test(value) &&
    Number.isFinite(Date.parse(`${value}T00:00:00Z`)) &&
    new Date(`${value}T00:00:00Z`).toISOString().slice(0, 10) === value
  if (!valid(range.startDate) || !valid(range.endDate)) return '请选择有效的开始和结束日期。'
  if (range.startDate < '1970-01-01' || range.endDate > '9998-12-31')
    return '统计日期需在 1970 至 9998 年之间。'
  const days = (Date.parse(range.endDate) - Date.parse(range.startDate)) / DAY + 1
  if (days < 1) return '开始日期不能晚于结束日期。'
  if (days > 366) return '一次最多查询 366 天，请缩小日期范围。'
  return ''
}
export function chartScale(values: number[], integer = false) {
  const minimum = Math.min(0, ...values),
    maximum = Math.max(0, ...values)
  const raw = (maximum - minimum || 1) / 4
  const magnitude = 10 ** Math.floor(Math.log10(raw))
  const step = Math.max(
    integer ? 1 : 0,
    [1, 2, 5, 10].find((value) => value * magnitude >= raw)! * magnitude,
  )
  const min = Math.floor(minimum / step) * step
  const max = Math.max(Math.ceil(maximum / step) * step, min + step)
  return {
    min,
    max,
    ticks: Array.from(
      { length: Math.round((max - min) / step) + 1 },
      (_, index) => min + step * index,
    ),
  }
}
