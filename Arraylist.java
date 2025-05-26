public class Arraylist { 

    private int[] arr;
    private int size;

    public Arraylist() {
        arr = new int[10];
        size = 0;
    }

    public void add(int value) {
        if (size == arr.length) {
            resize();
        }
        arr[size++] = value;
    }

    private void resize() {
        int[] newArr = new int[arr.length * 2];
        System.arraycopy(arr, 0, newArr, 0, arr.length);
        arr = newArr;
    }

    public int get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
        return arr[index];
    }

    public int size() {
        return size;
    }
    
}
