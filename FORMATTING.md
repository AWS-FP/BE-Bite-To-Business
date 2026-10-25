# Format Java

Dùng 4 dấu cách, dấu `{` cùng dòng khai báo và mỗi câu lệnh trên một dòng.
Mỗi annotation nằm trên dòng riêng, phía trên field, method hoặc tham số tương ứng.

```java
@NotBlank
@Size(max = 100)
private String name;

public String getName() {
    return name;
}
```

Cấu hình dùng chung nằm trong `formatter.xml`; `.editorconfig` bổ sung thiết lập cho IDE.
VS Code dùng profile `Bite to Business`. Các lệnh dưới đây chỉ xử lý file Java trong
`src/main/java` và `src/test/java`:

```sh
./mvnw formatter:format
./mvnw formatter:validate
```

Trên Windows dùng `mvnw.cmd` thay cho `./mvnw`.
Plugin và cấu hình tham khảo [Formatter Maven Plugin](https://code.revelc.net/formatter-maven-plugin/examples.html).
