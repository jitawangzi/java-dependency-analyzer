# Java依赖分析器

一个用于分析Java代码依赖关系并生成优化后的Markdown格式输出的工具，特别适合用于向大型语言模型（LLM）提供代码参考。通过智能分析和优化，能够将多个Java源文件合并成一个结构化文档，同时减少不必要的代码内容，使LLM能够更好地理解和处理代码。

## 主要功能

- **依赖分析**：自动分析Java类之间的依赖关系，生成依赖树
- **方法级分析**：识别并仅保留被引用的方法，减少不必要的代码
- **代码优化**：
  - 简化方法实现（保留方法签名，简化方法体）
  - 省略标准的getter/setter方法
  - 智能过滤导入声明
- **多种分析模式**：
  - 类分析模式：从一个待分析类开始，分析其依赖链
  - 目录分析模式：分析整个目录的文件，生成综合报告
- **令牌计数**：估算原始代码和优化后代码的token数量，计算节省比例
- **自动复制到剪贴板**：生成的结果自动复制到系统剪贴板，方便粘贴

## 系统要求

- Java 11或更高版本
- Maven 3.6或更高版本

## 使用方法
工具提供两种分析模式，可以通过运行不同的主类来选择：


### 类分析模式
类分析模式从一个指定的类开始，分析其依赖关系，生成包含所有相关类的优化代码。

修改application.properties配置文件：

```
# 待分析的类，主类
main.class=com.example.MyMainClass
# 设置项目根目录
project.root=/path/to/your/project
```
运行类分析器：
```
java -cp java-dependency-analyzer-1.0.0.jar io.github.jitawangzi.jdepend.ClassAnalyzer
```
### 目录分析模式
目录分析模式处理指定目录中的所有文件，根据配置的规则生成综合报告。

修改application.properties配置文件：
```
# 设置目录路径
directory.path=/path/to/your/directory
# 配置文件过滤规则
directory.include.files=*.java,*.xml
directory.exclude.files=*.test.java
```
运行目录分析器：

```
java -cp java-dependency-analyzer-1.0.0.jar io.github.jitawangzi.jdepend.DirectoryAnalyzer
```
#### 配置选项

配置文件位于src/main/resources/application.properties，以下是主要配置选项：

- 通用配置
```
# 输出文件名
output.file=output.md
# 类模式下表示类的引用分析深度，目录模式下表示从根目录的目录递归深度 (-1 表示不限制)
max.depth=-1
# 排除的包前缀，包内的文件都会跳过 (逗号分隔)
excluded.packages=com.example.generated
# 是否简化方法体，忽略方法实现。类分析模式下，代表对于主类引用的其他类的方法
# 在目录分析模式下， 表示所有的类的方法。  默认保留1行代码的方法体
simplify.ref.methods=true
# 是否省略getter/setter方法
omit.bean.methods=true
```
- 类分析模式配置
```
# 待分析的类，主类
main.class=com.example.MyMainClass
# 项目根目录
project.root=/path/to/your/project
# 是否保留主类方法体
keep.main.methods=true
# 是否只保留被引用的方法
keep.only.referenced.methods=true
```
- 目录模式配置
```
# 待分析的文件根目录，绝对路径
directory.path=/path/to/your/directory
# 暂时不用
directory.include.files=*.java,*.xml
# 排除的文件模式
directory.exclude.files=*.test.java
# 包含的目录
directory.include.folders=src/main
# 排除的目录
directory.exclude.folders=target,build
# 允许的文件扩展名
directory.allowed.extensions=java,xml,properties
```

使用示例

## 工作原理
- 依赖分析：工具使用JavaParser解析Java源代码，分析类之间的依赖关系
- 方法跟踪：识别方法调用链，确定哪些方法被实际引用
- 内容处理：简化方法实现，移除未使用的代码，优化导入声明
- 令牌优化：通过各种策略减少代码的token数量，使其更适合LLM的输入限制
- Markdown生成：将处理后的代码组织成结构化的Markdown文档

## 常见问题解答
### 如何减少生成文件的大小？
- 启用omit.bean.methods=true  省略getter  setter
- 设置max.depth限制分析深度

**类分析模式**
- 启用 keep.only.referenced.methods=true 排除没有引用到的方法。 
- method.body.max.depth 省略方法体
  
**目录模式** 
- 设置excluded.packages排除不需要的包
- simplify.methods=true 省略方法体

## 已知问题
- 内部类不会参与方法引用分析，也不会省略方法体。
- 在某些复杂代码情况下，JavaParser可能会解析失败，导致遗漏一些方法实际调用的类。

## 贡献
欢迎提交问题报告、功能请求和代码贡献。请先fork仓库，然后提交拉取请求。

## 许可证
本项目采用MIT许可证

