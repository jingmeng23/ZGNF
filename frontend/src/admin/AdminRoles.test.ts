import { readFileSync } from 'node:fs'
import { compileTemplate, parse } from '@vue/compiler-sfc'
import { expect, it } from 'vitest'

it('compiles role management event handlers in the Vue template', () => {
  const filename = new URL('./AdminRoles.vue', import.meta.url)
  const { descriptor, errors } = parse(readFileSync(filename, 'utf8'))
  expect(errors).toEqual([])
  const result = compileTemplate({
    source: descriptor.template!.content,
    filename: filename.pathname,
    id: 'admin-roles-regression',
    compilerOptions: { expressionPlugins: ['typescript'] },
  })
  expect(result.errors).toEqual([])
})
