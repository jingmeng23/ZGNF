declare module 'address-parse' {
  const parser: { parse(text: string, parseAll?: boolean): unknown[] }
  export default parser
}
