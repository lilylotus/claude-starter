package cn.nihility.rbac.fileupload.constant;

/**
 * 文件上传记录状态常量。业务语义是"正常/失效"而非"启用/停用"，与
 * {@code AdminStatus} 等状态常量类值相同但单独成类，避免跨领域概念耦合
 * （add-file-upload change design.md Decision 2）。
 */
public final class FileUploadStatus {

    /** 正常，允许下载。 */
    public static final int NORMAL = 2000;

    /** 失效，禁止下载。 */
    public static final int DISABLED = 3000;

    /**
     * 工具类不允许实例化。
     */
    private FileUploadStatus() {
    }
}
