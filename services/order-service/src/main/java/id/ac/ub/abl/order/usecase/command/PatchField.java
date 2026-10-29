package id.ac.ub.abl.order.usecase.command;

/** Membedakan field yang tidak dikirim dari field yang dikirim, termasuk null. */
public record PatchField<T>(boolean supplied, T value) {
    public PatchField {
        if (!supplied && value != null) {
            throw new IllegalArgumentException("Field yang tidak dikirim tidak boleh memiliki nilai");
        }
    }

    public static <T> PatchField<T> absent() {
        return new PatchField<>(false, null);
    }

    public static <T> PatchField<T> supplied(T value) {
        return new PatchField<>(true, value);
    }
}
