@Idempotent// 幂等性验证注解
@Target(ElementType.METHOD)//目标范围注解，标签只能贴在方法上面source
@Retention(RetentionPolicy.RUNTIME)//保留、存活时间注解，分为{source、class、runtime}三种，对应三种策略；source只在源码里存在，编译完就扔了，class留在 class 文件里，但运行时 JVM 读不到（默认值），runtime运行时还在，可以通过反射读到
@interface //注解，表示是一个接口
@Aspect      // 声明这是一个切面：里面装着“在什么时候、对哪些方法、做什么事”
@Component   // 交给 Spring 容器管理；不加的话 Spring 根本不知道它的存在，注解就白贴了
@Around  //环绕通知，威力最大的一种。它在目标方法前后都能插代码，而且能决定“要不要执行”“要不要换返回值”“要不要吞掉异常”。只有 @Around 才能阻止方法执行，@Before/@After 只能旁观，拦不住。
@annotation(idempotent)//切入点表达式，意思是“所有带了 @Idempotent 注解的方法”。括号里的 idempotent 是个名字绑定：Spring 会把那个注解的实例当成参数传进来。所以方法第二个参数必须叫 Idempotent idempotent，名字对上才能拿到
within(...) 的意思是「匹配指定类型范围内的所有方法执行」
@Data（Lombok）//Lombok 的注解，编译时自动生成：所有字段的 getter/setter + toString() + equals()/hashCode() + 一个全参的 @RequiredArgsConstructor（这里没有 final 字段，所以生成的是无参构造）。
@TableId：告诉 MyBatis-Plus「这个字段是主键」。默认按字段名 id 去匹配数据库列，如果数据库列叫 user_id，就要写成 @TableId("user_id")。
@TableField 是「字段级配置」，fill = FieldFill.INSERT 的意思是：执行 insert 时，让「自动填充处理器」来给这个字段赋值。
@TableLogic private Integer isDeleted;软删除：标记数据「已删除」但不真删，数据库里那行还在。
@EnumValue：告诉 MyBatis-Plus 怎么存数据库
@JsonValue：告诉 Jackson 怎么转 JSON
@ControllerAdvice = 「我是一个全局的控制器增强」。它会让 Spring 把这个类注册到所有 @RestController 的异常处理链上，不用你在每个 Controller 里重复写 @ExceptionHandler。
@ResponseBody = 「方法返回值直接序列化成 JSON 写进响应体」，而不是去找视图模板。所以你的 Result<Void> 会变成 {"code":400,"msg":"...","data":null}。
@ResponseStatus 的作用：它不改 Result 的内容，改的是 HTTP 响应头的状态码

MDC（Mapped Diagnostic Context，映射诊断上下文）是 SLF4J 提供的一个「线程级别的日志变量袋」。 你往里面塞键值对，然后在日志模板里用 %X{key} 引用它，后续这个线程打的所有日志都会自动带上这些值，而你一行打印代码都不用改。
IService<T> 是 MyBatis-Plus 提供的通用服务接口，继承它之后，CartService 自动拥有增删改查等能力（不用自己写）