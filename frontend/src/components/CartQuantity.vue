<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Minus, Plus } from 'lucide-vue-next'
import type { CartItem } from '../api/types'
import { useCartStore } from '../stores/cart'
const props = defineProps<{ item: CartItem; disabled?: boolean }>()
const emit = defineEmits<{ pending: [id: string, pending: boolean] }>()
const cart = useCartStore(),
  draft = ref(String(props.item.quantity)),
  error = ref(''),
  saving = ref(false),
  dirty = ref(false)
const maximum = computed(() => Math.min(props.item.availableStock, 999))
let timer: ReturnType<typeof setTimeout> | undefined,
  sequence = 0,
  submittedSequence = -1,
  inflight = 0,
  mounted = true
const validation = computed(() =>
  /^\d+$/.test(draft.value) &&
  Number.isSafeInteger(Number(draft.value)) &&
  Number(draft.value) >= 1 &&
  Number(draft.value) <= maximum.value
    ? ''
    : `请输入 1 至 ${maximum.value} 的整数`,
)
watch(
  () => props.item.quantity,
  (value) => {
    if (!dirty.value && !saving.value) draft.value = String(value)
  },
)
function edit(delay = 400) {
  sequence++
  clearTimeout(timer)
  dirty.value = true
  error.value = validation.value
  emit('pending', props.item.id, true)
  if (error.value) return
  timer = setTimeout(() => {
    timer = undefined
    void commit()
  }, delay)
}
function step(delta: number) {
  draft.value = String(
    Math.max(1, Math.min(maximum.value, (Number(draft.value) || props.item.quantity) + delta)),
  )
  edit(200)
}
async function commit() {
  clearTimeout(timer)
  timer = undefined
  if (validation.value) {
    error.value = validation.value
    return
  }
  if (!dirty.value || submittedSequence === sequence) return
  const token = sequence,
    quantity = Number(draft.value)
  submittedSequence = token
  inflight++
  saving.value = true
  error.value = ''
  try {
    await cart.setQuantity(props.item.id, quantity)
  } catch (e) {
    if (token === sequence && mounted) error.value = (e as Error).message
  } finally {
    inflight--
    if (mounted) saving.value = inflight > 0
    if (token === sequence && mounted) {
      draft.value = String(
        cart.items.find((i) => i.id === props.item.id)?.quantity ?? props.item.quantity,
      )
      dirty.value = false
      emit('pending', props.item.id, saving.value)
    }
  }
}
function revert() {
  if (saving.value) return
  sequence++
  clearTimeout(timer)
  timer = undefined
  draft.value = String(props.item.quantity)
  dirty.value = false
  error.value = ''
  emit('pending', props.item.id, false)
}
onBeforeUnmount(() => {
  mounted = false
  sequence++
  clearTimeout(timer)
  emit('pending', props.item.id, false)
})
</script>

<template>
  <div class="cart-quantity">
    <div class="quantity-control" :aria-busy="saving">
      <button
        type="button"
        :aria-label="`减少${item.productName}数量`"
        :disabled="disabled || Number(draft) <= 1"
        @click="step(-1)"
      >
        <Minus :size="14" aria-hidden="true" /></button
      ><input
        v-model="draft"
        inputmode="numeric"
        maxlength="4"
        :aria-label="`${item.productName}购买数量`"
        :aria-invalid="!!error"
        :aria-describedby="error ? `quantity-error-${item.id}` : undefined"
        :disabled="disabled"
        @input="edit()"
        @blur="commit"
        @keydown.enter.prevent="commit"
        @keydown.esc.prevent="revert"
      /><button
        type="button"
        :aria-label="`增加${item.productName}数量`"
        :disabled="disabled || Number(draft) >= maximum"
        @click="step(1)"
      >
        <Plus :size="14" aria-hidden="true" />
      </button>
    </div>
    <small v-if="error" :id="`quantity-error-${item.id}`" class="quantity-error" role="alert">{{
      error
    }}</small
    ><small v-else-if="dirty || saving" class="quantity-status" role="status">正在更新…</small
    ><small v-else-if="item.availableStock < 10" class="quantity-status"
      >剩余 {{ item.availableStock }} 件</small
    >
  </div>
</template>

<style scoped>
.cart-quantity {
  min-width: 0;
}
.quantity-control {
  display: flex;
  width: 116px;
  min-height: 40px;
  overflow: hidden;
  border: 1px solid #ddd;
  border-radius: 5px;
  background: #fff;
}
.quantity-control button {
  display: grid;
  place-items: center;
  width: 34px;
  flex-shrink: 0;
  border: 0;
  background: #fafafa;
  color: #444;
}
.quantity-control button:disabled {
  color: #aaa;
  cursor: not-allowed;
}
.quantity-control input {
  width: 46px;
  min-width: 0;
  border: 0;
  border-left: 1px solid #eee;
  border-right: 1px solid #eee;
  text-align: center;
  color: #333;
  background: #fff;
  font-size: 13px;
}
.quantity-error,
.quantity-status {
  display: block;
  font-size: 11px;
  margin-top: 7px;
  line-height: 1.5;
}
.quantity-error {
  color: #a13223;
}
.quantity-status {
  color: #777;
}
@media (max-width: 620px) {
  .quantity-control {
    min-height: 44px;
    width: 130px;
  }
  .quantity-control button {
    width: 40px;
  }
  .quantity-control input {
    width: 48px;
    font-size: 16px;
  }
}
</style>
