/*******************************************************************************
 * Copyright (c) 2017 Kichwa Coders Ltd. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
#include <stdio.h>
#include <string.h>
#include <pthread.h>
#include <stdlib.h>
#include <unistd.h>


#define FOREVER
#define NUMTHREADS 3

pthread_t tid[NUMTHREADS];
int threadNums[NUMTHREADS];

void *doSomeThing(void *arg) {
  int threadNum = *(int *)arg;
  printf("Running thread %d\n", threadNum);

  // Fake some work
  for (int i = 0; i < 0xfffffff; i++)
    ;

  printf("Finishing thread %d\n", threadNum);
  return NULL;
}

int main(void) {
  int err = setvbuf(stdout, NULL, _IONBF, 0);
  if (err != 0) {
      printf("Failed to setvbuf: %s\n", strerror(err));
  }

#ifdef FOREVER
  while (1) {
#endif
  for (int i = 0; i < NUMTHREADS; i++) {
    threadNums[i] = i;
    int err = pthread_create(&tid[i], NULL, &doSomeThing, &threadNums[i]);
    if (err != 0) {
      printf("Failed to create thread %d: %s\n", i, strerror(err));
    } else {
      printf("Created thread %d\n", i);
    }
  }

  printf("Joining threads\n");
  for (int i = 0; i < NUMTHREADS; i++) {
    int err = pthread_join(tid[i], NULL);
    if (err != 0) {
      printf("Failed to join thread %d: %s\n", i, strerror(err));
    } else {
      printf("Joined thread %d\n", i);
    }
  }
#ifdef FOREVER
  printf("Sleeping before restarting.\n");
  sleep(3);
  }
#endif

  
  printf("Main thread done\n");
  return 0;
}
