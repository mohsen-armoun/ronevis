package mt.storage;

class StorageException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public StorageException(String msg) {
        super(msg);
    }

    public StorageException(Throwable t) {
        super(t);
    }
}
