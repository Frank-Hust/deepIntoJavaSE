package Reflection;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ReflectionTimeAnalysis {

	private static int createTimes = 1000000;
	private static Random random = new Random();
	public float f;

	public ReflectionTimeAnalysis() {
		super();
	}

	public static void main(String[] args) throws Exception {
		doRegular();
		doReflection();
	}

	private static void doReflection() throws Exception {
		long startTime = System.currentTimeMillis();
//		把生成的对象加入list,防止生成过程被优化
		List<ReflectionTimeAnalysis> list = new ArrayList<>();
		for (int i = 0; i < createTimes; i++) {
			ReflectionTimeAnalysis object = (ReflectionTimeAnalysis) Class
					.forName("Reflection.ReflectionTimeAnalysis").newInstance();
			object.f = random.nextFloat();
			list.add(object);
		}
		long endTime = System.currentTimeMillis();
		System.out.println("reflection cost " + (endTime - startTime) + "ms");//reflection cost 2864ms
	}

	private static void doRegular() {
		long startTime = System.currentTimeMillis();
		List<ReflectionTimeAnalysis> list = new ArrayList<>();
		for (int i = 0; i < createTimes; i++) {
			ReflectionTimeAnalysis object = new ReflectionTimeAnalysis();
			object.f = random.nextFloat();
			list.add(object);
		}
		long endTime = System.currentTimeMillis();
		System.out.println("regular cost " + (endTime - startTime) + "ms");//regular cost 68ms
	}

}
