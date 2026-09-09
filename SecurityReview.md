# JMComic-Crawler-Python 静态安全审查

[English summary](SecurityReview.en.md)

上游审查日期：2026-09-05。Android 发布复查日期：2026-09-09。状态：静态审查完成；独立 Android 实现未把被审 Python 包或插件系统打入 APK。

## 结论

**在本次审查的源码快照及抽查的依赖路径中，未发现可确认的隐蔽恶意植入、挖矿、勒索、开机持久化或向固定攻击者地址窃取凭据的证据。发现了命令注入、不安全配置反序列化、路径越界和危险删除等风险，不能将结论解释为“没有漏洞”或“保证安全”。**

可以据此制定移动端项目计划，但应将安全收敛和依赖复审作为集成前置条件，不宜直接运行仓库工作流或原样开放插件配置。

## 审查对象与可复核范围

| 项目 | 记录 |
| --- | --- |
| 用户指定仓库 | [theabab23333/JMComic-Crawler-Python](https://github.com/theabab23333/JMComic-Crawler-Python) |
| 精确提交 | [`f2adb337881d96de65c6862935c9f1c32ce847f4`](https://github.com/theabab23333/JMComic-Crawler-Python/commit/f2adb337881d96de65c6862935c9f1c32ce847f4) |
| 提交时间 / 源码版本 | 2025-09-21 22:00:22 +08:00 / `2.6.9` |
| Git tree | `6afcaf95a9878efa2df16dab85b88b25e582cd13` |
| 快照规模 | 77 个跟踪文件，其中 20 个 Python 文件 |
| 来源交叉核对 | 从原作者 `hect0x7/JMComic-Crawler-Python` 亦成功取得同一提交；此快照内容相同。GitHub API 对用户指定仓库返回 `fork: false`，因此不将其平台关系写成“已确认的 fork”。 |
| 审查工作副本 | 发布者本机的隔离审查目录；不随 Android 源码仓库分发 |
| 证据清单 | 发布者本机保留逐文件 Git blob、SHA-256、Python AST 检查结果及版本信息 |
| 依赖样本 | PyPI `commonx==0.6.38`，仅下载 wheel 并核对摘要，没有安装或导入 |
| wheel SHA-256 | `3b35d37e9cceee61f5613e99b78dc23d4594eca546f182a8e87d8056015f6cee` |

采用全仓文本检索、20 个 Python 文件的 AST 解析、关键调用链人工检查及并行复核。检查安装入口、模块导入、CLI、网络请求、插件、配置、图像处理、文件写入/删除、自动化工作流、示例和测试。20 个 Python 文件均可被 AST 解析；这仅是检查辅助，不是安全认证。

`commonx==0.6.38` 是 `pyproject.toml` 声明的最低版本样本，不代表未来执行 `pip install` 会安装这个版本。本次只进一步检查其配置解析、路径、请求适配、邮件及相关导入路径，没有审计全部依赖及其原生二进制。

没有安装项目依赖、导入或执行被审源码，没有运行它的测试/工作流，没有登录账号、访问业务站点或下载内容。未检查完整 Git 历史、其他分支、发行附件、PyPI 的 jmcomic 发布包及完整依赖漏洞数据库。网站与远程域名当前是否可信、API 当前是否可用，也不在已验证结论内。升级提交或依赖后需要重新核对变化。

## 主要发现

以下级别用于本次集成优先级判断，并非 CVSS 评分；静态可达性不等于已经发生攻击。

### S1 · 高：不可信配置可触发 pickle 反序列化

`JmOption.from_file()` 在构造校验前直接调用 `PackerUtil.unpack()`。在已检查的 commonx 0.6.38 中，该函数按文件扩展名选择解析器，支持 `.pickle` 并调用 `pickle.load()`。如果使用者加载攻击者提供的此类配置，可能在读取配置时执行代码。正常 JSON / YAML 配置不会自动走这条路径；该依赖样本的 YAML 使用 `SafeLoader`，不应误报为不安全 YAML。

证据：[jm_option.py 第 340 行起](https://github.com/theabab23333/JMComic-Crawler-Python/blob/f2adb337881d96de65c6862935c9f1c32ce847f4/src/jmcomic/jm_option.py#L340)；本地依赖样本 `common/base/packer.py` 第 62–76、134–178 行；[PyPI 样本元数据](https://pypi.org/pypi/commonx/0.6.38/json)。

集成要求：应用只接受字段白名单明确的设置，采用严格 JSON schema；不开放任意 option 文件导入、pickle 或任意插件定义。实际选用的 commonx 版本需另行复审。

### S2 · 高：可选收藏夹压缩功能存在 shell 命令注入

启用收藏夹导出并设置压缩密码后，代码将保存目录、输出路径、密码拼入命令，再通过 `subprocess.run(..., shell=True)` 执行。配置字段中的 shell 特殊字符可能被当作命令执行。该命令还会被日志打印，其中包含压缩密码。需要走到这个显式插件的压缩步骤才会触发，不能据此认定存在默认运行的木马。

证据：[jm_plugin.py 第 728 行起](https://github.com/theabab23333/JMComic-Crawler-Python/blob/f2adb337881d96de65c6862935c9f1c32ce847f4/src/jmcomic/jm_plugin.py#L728)，以及同文件第 89–99 行的命令执行封装。

集成要求：首版禁用此功能；后续确需外部工具时使用参数数组、`shell=False` 和固定可执行文件，不在日志输出密码。

### S3 · 中高：特殊章节名可使文件写出下载根目录

默认路径规则为 `Bd_Pname`。路径生成对章节名进行字符替换后直接拼接；commonx 0.6.38 的辅助函数没有拒绝字面值 `..`，也没有检查解析后的路径是否仍位于根目录。若远程章节名恰为 `..`，构造出的路径为 `base_dir/..`，图片可能写入父目录。此项是结合指定依赖样本贯通的静态结论，尚未做运行时攻击验证，也不声称任意路径都能直接利用。

证据：[jm_option.py 第 78 行起](https://github.com/theabab23333/JMComic-Crawler-Python/blob/f2adb337881d96de65c6862935c9f1c32ce847f4/src/jmcomic/jm_option.py#L78)、第 243–259 行；`jm_config.py` 第 414 行；依赖样本 `common/util/file_util.py` 第 5–27 行。

集成要求：内部目录使用经过校验的作品 ID / 章节 ID / 图片序号；标题只作展示。写入、读取、导出和删除均需验证最终路径，拒绝空片段、`.`、`..`、绝对路径及符号链接等越界情况。

### S4 · 中高：可选去重插件可能删除整个下载根目录内的文件

去重插件默认 `delete_original_file=True`，达到重复次数阈值后删除同 MD5 的整组文件，不保留其中一份；`limit` 未验证，设置为 1 或更小时可能删除遍历到的全部文件。默认路径规则没有作品层级，插件认为的“作品根目录”会退化为整个 `base_dir`。该行为来自显式启用的去重功能，不是默认无条件删除。

证据：[jm_plugin.py 第 1170 行起](https://github.com/theabab23333/JMComic-Crawler-Python/blob/f2adb337881d96de65c6862935c9f1c32ce847f4/src/jmcomic/jm_plugin.py#L1170)，以及 `jm_option.py` 第 75–82 行。

集成要求：禁用原去重删除插件；只允许按应用自己的文件清单清理。默认保留源图，PDF 校验失败不得清理图片。

### S5 · 中：依赖、发布工作流和被审源码存在供应链边界

构建依赖、运行依赖和开发依赖没有完整的精确版本及哈希锁定。下载工作流的非 dev 分支会执行 `pip install jmcomic ... --upgrade`，因此实际运行包不一定是本次检查的源码。开发依赖还包含 `plugin_jm_server` 等额外包。若运行仓库下载工作流，账号/邮件 secrets 被放入整个 job 的环境，安装步骤也能读取这些变量。

另有条件性发布 CI 命令注入：`.github/release.py` 从提交消息提取 tag，拼接 shell 后交给 `os.system()`；对应自动发布 job 在 master 上以 `v` 开头的提交消息触发，且有写仓库和 OIDC 权限。含 shell 替换表达式的 tag 可形成执行路径；它不在日常库调用路径上。

证据：[pyproject.toml](https://github.com/theabab23333/JMComic-Crawler-Python/blob/f2adb337881d96de65c6862935c9f1c32ce847f4/pyproject.toml#L27)、[download.yml](https://github.com/theabab23333/JMComic-Crawler-Python/blob/f2adb337881d96de65c6862935c9f1c32ce847f4/.github/workflows/download.yml#L14)、[release.py](https://github.com/theabab23333/JMComic-Crawler-Python/blob/f2adb337881d96de65c6862935c9f1c32ce847f4/.github/release.py#L7)、[release_auto.yml](https://github.com/theabab23333/JMComic-Crawler-Python/blob/f2adb337881d96de65c6862935c9f1c32ce847f4/.github/workflows/release_auto.yml#L6)。

集成要求：独立构建移动项目；不照搬上游 CI。锁定实际选择的代码和所有直接/间接依赖、记录产物哈希；安装阶段不提供用户秘密。发布元数据写文件，不经过 shell。

### S6 · 中：自动域名更新会扩大网络信任范围

默认开启 API 域名自动更新，初始化客户端时访问配置中的 BytePlus 对象存储地址，解密后取 JSON 的 `Server` 列表替换内置域名。此链路用于业务域名发现，不是远程下载 Python 执行；但未看到域名列表独立签名校验及应用级允许列表。远程配置源若遭修改，可能将后续业务请求导向不同地址。

证据：[jm_config.py 第 137 行起](https://github.com/theabab23333/JMComic-Crawler-Python/blob/f2adb337881d96de65c6862935c9f1c32ce847f4/src/jmcomic/jm_config.py#L137)、[jm_client_impl.py 第 1007 行起](https://github.com/theabab23333/JMComic-Crawler-Python/blob/f2adb337881d96de65c6862935c9f1c32ce847f4/src/jmcomic/jm_client_impl.py#L1007)。

集成要求：禁用不受控的自动域名替换，使用管理员维护的允许列表并验证 HTTPS。服务器应约束图片 URL、重定向和 DNS 解析结果，阻止请求回环、内网及云元数据地址；允许列表更新先复核。当前域名连通性需在后续隔离联调中确认。

### S7 · 中：敏感插件与日志需要限制

浏览器 Cookie 插件需显式配置后才会调用浏览器数据读取；邮件插件将配置中的内容发送至配置收件人。配置字符串支持环境变量替换，因而恶意配置还可能组合这些功能外传环境变量。默认配置未启用这些插件；常规登录请求发送到选定业务域名，并对登录重试日志做了部分遮盖，未发现另设固定攻击者地址。

重试日志对非登录请求会打印参数；工作流下载脚本会保存异常上下文/响应正文，再将下载目录上传为 GitHub artifact。手动收藏夹导出工作流还接受普通字符串形式的密码输入。这些是条件性隐私暴露面，不等于已经证明密码被泄露。

证据：[jm_plugin.py 第 526 行起](https://github.com/theabab23333/JMComic-Crawler-Python/blob/f2adb337881d96de65c6862935c9f1c32ce847f4/src/jmcomic/jm_plugin.py#L526)、同文件第 566–614 行；`jm_option.py` 第 629–632 行；[workflow_download.py 第 121 行起](https://github.com/theabab23333/JMComic-Crawler-Python/blob/f2adb337881d96de65c6862935c9f1c32ce847f4/usage/workflow_download.py#L121)。commonx 样本 `common/ext/qq_email.py` 的收件人也来自传入配置，SMTP 服务为 `smtp.qq.com`。

集成要求：首版不启用 Cookie 自动读取、邮件或环境变量模板输入；日志脱敏且不默认上传。初期采用单用户后端，避免直接共享库内全局 Cookie / 域名状态到多用户服务。

### S8 · 中：内置服务插件不适合作为未经审查的手机后端

`jm_server` 插件默认监听 `0.0.0.0:80`，密码参数默认空字符串，实际服务委托外部 `plugin_jm_server`。本次没有审查该外部服务的认证实现，因此不能断言它一定无鉴权，也不能视为安全的成品后端。

证据：[jm_plugin.py 第 900 行起](https://github.com/theabab23333/JMComic-Crawler-Python/blob/f2adb337881d96de65c6862935c9f1c32ce847f4/src/jmcomic/jm_plugin.py#L900)。

集成要求：建立独立、带鉴权的 API 和文件访问层，不直接开启这个插件。

## PDF 与产品能力核实

仓库确实已有 [Img2pdfPlugin](https://github.com/theabab23333/JMComic-Crawler-Python/blob/f2adb337881d96de65c6862935c9f1c32ce847f4/src/jmcomic/jm_plugin.py#L741)，可按章节或作品合并图片，支持可选密码。转换函数遍历章节目录，并在转换前以 `wb` 打开最终 PDF。commonx 0.6.38 的 `files_of_dir()` 使用字典序排序，包含目录内所有项目；并非完全未排序，但无法保证一般的数字页序或限定为本次有效图片。失败可能截断旧 PDF，也未见章节书签、持久化书库或阅读 UI。

下载器已有作品→章节→图片结构、回调、重试和缓存基础，可以受控复用。不过缓存命中会直接返回，下载成功列表不能直接充当完整图片清单。新应用必须自己维护顺序、完整性、恢复状态和 PDF 发布过程。

## Android 1.2.0 发布复查

Android 项目逐项检查了 Manifest 权限、网络端点、重定向行为、文件路径、删除范围、导出范围、备份配置、动态执行入口、敏感数据持久化、依赖锁定和签名边界。发布源码中未发现 shell/进程执行、动态代码加载、WebView JavaScript 桥、无障碍服务、设备管理、全盘存储、短信、通讯录、位置、相机、麦克风、广告或统计 SDK。

移动端仅申请联网、通知及数据同步前台服务权限。网络安全配置拒绝明文流量；OkHttp 客户端关闭重定向，仅构造代码内允许的 HTTPS API 与图片主机，并限制 API 和图片响应大小。协议常量是上游公开客户端兼容值，不属于用户密钥。临时会话 Cookie 按主机隔离且只保存在进程内存中。

作品、章节和图片路径只接受数字 ID 或受限图片文件名。删除和导出操作解析 canonical path 并验证目标仍在应用私有根目录内；PDF 通过临时文件完成后替换，下载图片先写 `.part` 再发布。应用关闭 Android 云备份和设备迁移，PDF 对外保存使用系统文档选择器。

依赖版本由 Gradle 锁文件固定，构件摘要由 Gradle dependency verification 元数据校验。公开 CI 使用只读仓库权限，不接收正式签名材料。正式签名密钥及密码保存在仓库外，并由环境变量注入构建。

复查局限：这是源码静态审查、单元测试、Android Lint 和离线构建验证，不是形式化证明或第三方安全认证；未对第三方业务服务当前运营方、网络基础设施及所提供内容作安全或合法性担保。后续修改网络主机、依赖、权限、签名流程或文件操作后应重新复查。

## 集成与发布判断

**被审上游 Python 项目不适合原样打包进手机应用；Android 1.2.0 的独立实现已切断上述危险配置和插件路径，并满足公开源码与正式 APK 发布的静态安全门槛。**

仓库采用 [MIT 许可](https://github.com/theabab23333/JMComic-Crawler-Python/blob/f2adb337881d96de65c6862935c9f1c32ce847f4/LICENSE)，复用分发时保留其版权及许可文本；该代码许可不自动赋予下载内容的使用权。
