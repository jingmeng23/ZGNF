import { expect, it, vi } from 'vitest'
import { chartScale, presetRange, shanghaiToday, shiftDate, validateRange } from './overview'

it('uses Shanghai local calendar days when a request crosses the UTC day boundary', () => {
  expect(shanghaiToday(new Date('2026-11-11T16:15:00.000Z'))).toBe('2026-11-12')
  expect(shanghaiToday(new Date('2026-11-12T16:15:00.000Z'))).toBe('2026-11-13')
})

it('calculates inclusive today, yesterday, seven-day, and thirty-day presets', () => {
  expect(presetRange('today', '2026-03-01')).toEqual({
    startDate: '2026-03-01',
    endDate: '2026-03-01',
  })
  expect(presetRange('yesterday', '2026-03-01')).toEqual({
    startDate: '2026-02-28',
    endDate: '2026-02-28',
  })
  expect(presetRange('7', '2026-03-01')).toEqual({ startDate: '2026-02-23', endDate: '2026-03-01' })
  expect(presetRange('30', '2024-03-01')).toEqual({
    startDate: '2024-02-01',
    endDate: '2024-03-01',
  })
  expect(shiftDate('2024-01-01', -1)).toBe('2023-12-31')
})

it('rejects invalid, reversed, and overlong ranges while allowing a leap-day boundary', () => {
  expect(validateRange({ startDate: '2026-02-30', endDate: '2026-03-01' })).toContain('有效')
  expect(validateRange({ startDate: '2026-03-02', endDate: '2026-03-01' })).toContain('晚于')
  expect(validateRange({ startDate: '2026-01-01', endDate: '2027-01-02' })).toContain('366')
  expect(validateRange({ startDate: '1969-12-31', endDate: '1970-01-01' })).toContain('1970')
  expect(validateRange({ startDate: '9998-12-31', endDate: '9999-01-01' })).toContain('9998')
  expect(validateRange({ startDate: '2024-02-29', endDate: '2024-03-01' })).toBe('')
})

it('scales positive, negative refund, and zero lines with visible integer ticks', () => {
  expect(chartScale([0, 10, 20])).toMatchObject({ min: 0, max: 20, ticks: [0, 5, 10, 15, 20] })
  const refunds = chartScale([-60, 0, 10])
  expect(refunds.min).toBeLessThanOrEqual(-60)
  expect(refunds.max).toBeGreaterThanOrEqual(10)
  expect(refunds.ticks.every(Number.isFinite)).toBe(true)
  expect(chartScale([0, 0, 0])).toEqual({ min: 0, max: 0.5, ticks: [0, 0.5] })
})

it('does not move the current date when choosing past-day ranges', () => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date('2026-11-11T16:30:00.000Z'))
  try {
    expect(presetRange('yesterday')).toEqual({ startDate: '2026-11-11', endDate: '2026-11-11' })
    expect(presetRange('7')).toEqual({ startDate: '2026-11-06', endDate: '2026-11-12' })
  } finally {
    vi.useRealTimers()
  }
})

it('uses whole-order ticks for small and empty order series without rounding money values', () => {
  for (const values of [
    [0, 0],
    [0, 1],
    [1, 2, 3],
  ]) {
    const scale = chartScale(values, true)
    expect(scale.ticks.every(Number.isInteger)).toBe(true)
    expect(scale.min).toBe(0)
    expect(scale.max).toBeGreaterThanOrEqual(Math.max(...values, 1))
  }
  expect(chartScale([0, 0.1, 0.2]).max).toBeLessThan(1)
})
