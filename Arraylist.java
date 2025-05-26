public class ArrayList<E> {
    private Object[] arr;
    private int size;

    public ArrayList() {
        arr = new Object[10];
        size = 0;
    }

    public void add(E value) {
        if (size == arr.length) {
            resize();
        }
        arr[size++] = value;
    }

    private void resize() {
        Object[] newArr = new Object[arr.length * 2];
        System.arraycopy(arr, 0, newArr, 0, arr.length);
        arr = newArr;
    }

    public E get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
        return (E) arr[index];
    }

    public int size() {
        return size;
    }

    public void clear() {
        size = 0;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public boolean contains(E value) {
        for (int i = 0; i < size; i++) {
            if (arr[i].equals(value)) return true;
        }
        return false;
    }

    public boolean remove(E value) {
        for (int i = 0; i < size; i++) {
            if (arr[i].equals(value)) {
                for (int j = i; j < size - 1; j++) {
                    arr[j] = arr[j + 1];
                }
                size--;
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < size; i++) {
            sb.append(arr[i]);
            if (i < size - 1) sb.append(", ");
        }
        sb.append("]");
        return sb.toString();
    }
}
