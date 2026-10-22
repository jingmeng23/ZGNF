import type { AdminColumn, AdminRow } from './adminData'

export function toCsv(columns: AdminColumn[], rows: AdminRow[]): string {
  const escape = (value: unknown) => {
    let text = String(value ?? '')
    // Do not interpret exported text as spreadsheet formulas.
    if (typeof value === 'string' && /^\s*[=+\-@]/.test(text)) text = `'${text}`
    return `"${text.replaceAll('"', '""')}"`
  }
  return [
    columns.map((column) => escape(column.label)).join(','),
    ...rows.map((row) => columns.map((column) => escape(row[column.key])).join(',')),
  ].join('\r\n')
}
