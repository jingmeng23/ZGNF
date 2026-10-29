import { request } from './http'
import type { PageResult } from './types'
export interface RoleRecord { id: string; name: string; roleKey: string; status: 'active'|'inactive'; permissionIds: string[]; members: number; system: boolean; createTime: string; updateTime: string }
export interface PermissionRecord { id: string; name: string; permissionKey: string }
export interface RoleQuery { page:number; size:number; keyword:string; status:string }
const root='/admin/roles', path=(id:string)=>`${root}/${encodeURIComponent(id)}`
export const rolesApi={
 list:(q:RoleQuery)=>{const p=new URLSearchParams();for(const [k,v] of Object.entries(q))if(v!=='')p.set(k,String(v));return request<PageResult<RoleRecord>>(`${root}?${p}`)},
 permissions:()=>request<PermissionRecord[]>(`${root}/permissions`), detail:(id:string)=>request<RoleRecord>(path(id)),
 create:(input:{name:string;roleKey:string;status:'active'|'inactive'})=>request<RoleRecord>(root,{method:'POST',body:JSON.stringify(input)}),
 update:(id:string,input:{name:string;roleKey:string;status:'active'|'inactive'})=>request<RoleRecord>(path(id),{method:'PUT',body:JSON.stringify(input)}),
 assignPermissions:(id:string,ids:string[])=>request<RoleRecord>(`${path(id)}/permissions`,{method:'PUT',body:JSON.stringify({ids})}),
 remove:(id:string)=>request<void>(path(id),{method:'DELETE'}),
}
